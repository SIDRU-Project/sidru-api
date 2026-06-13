package com.sidru.sidru_api.blockchain.infrastructure.web3j;

import com.sidru.sidru_api.blockchain.application.internal.custodial.CustodialWalletService;
import com.sidru.sidru_api.blockchain.domain.model.aggregates.BlockchainTransaction;
import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.BlockchainTransactionRepository;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.BlockchainProperties;
import com.sidru.sidru_api.notifications.interfaces.acl.NotificationContextFacade;
import com.sidru.sidru_api.sessions.application.internal.outboundservices.blockchain.BlockchainPort;
import com.sidru.sidru_api.sessions.domain.model.aggregates.RecyclingSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Hash;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Real Web3j implementation of the {@link BlockchainPort} outbound port.
 *
 * <p>Resolves the citizen's custodial address, mints CTC via {@code recordAndReward}
 * (1 point = 1 CTC = 10^18 wei) and persists a {@link BlockchainTransaction}. Honors
 * {@code BLOCKCHAIN_ENABLED}, is idempotent per sessionId, retries transient RPC
 * failures with backoff (RF-18), and never breaks the off-chain session confirmation.
 *
 * <p>Confirmation strategy (E2E fix): the {@link TransactionReceipt} returned by
 * {@code recordAndReward} is itself proof of block inclusion, so on a status-OK receipt
 * the {@link BlockchainTransaction} is persisted with {@code confirmed=true} and a
 * best-effort FCM is fired here (US-39). The TokensMinted event listener remains as an
 * idempotent fallback: if it ever runs, the already-confirmed tx short-circuits it, so
 * there is no duplicate notification.
 */
@Service
public class Web3jBlockchainAdapter implements BlockchainPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(Web3jBlockchainAdapter.class);
    private static final String NETWORK = "polygon-amoy";
    private static final BigInteger WEI_PER_CTC = BigInteger.TEN.pow(18);
    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_BASE_MILLIS = 500L;
    private static final String NOTIFICATION_TITLE = "¡Tokens CTC acreditados!";

    private final BlockchainTransactionRepository txRepository;
    private final CustodialWalletService custodialWalletService;
    private final ChapaTuCriptoContract contract;
    private final BlockchainProperties properties;
    private final NotificationContextFacade notificationContextFacade;

    public Web3jBlockchainAdapter(BlockchainTransactionRepository txRepository,
                                  CustodialWalletService custodialWalletService,
                                  ChapaTuCriptoContract contract,
                                  BlockchainProperties properties,
                                  NotificationContextFacade notificationContextFacade) {
        this.txRepository = txRepository;
        this.custodialWalletService = custodialWalletService;
        this.contract = contract;
        this.properties = properties;
        this.notificationContextFacade = notificationContextFacade;
    }

    @Override
    @Transactional
    public Optional<String> recordSession(RecyclingSession session) {
        if (!properties.isEnabled()) {
            LOGGER.debug("Blockchain disabled — skipping tx for session {}", session.getId());
            return Optional.empty();
        }

        // Idempotency: a tx for this session already exists -> reuse its hash.
        var existing = txRepository.findBySessionId(session.getId());
        if (existing.isPresent()) {
            LOGGER.info("Session {} already has a blockchain tx {} — reusing",
                    session.getId(), existing.get().getTxHash());
            return Optional.of(existing.get().getTxHash());
        }

        String address = custodialWalletService.addressFor(session.getUserId());
        BigInteger sessionId = BigInteger.valueOf(session.getId());
        BigInteger amount = BigInteger.valueOf(session.getPointsEarned()).multiply(WEI_PER_CTC);
        byte[] qrHash = Hash.sha3(session.getQrToken().getBytes(StandardCharsets.UTF_8));

        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                TransactionReceipt receipt = contract.recordAndReward(address, sessionId, qrHash, amount);
                if (receipt == null || !receipt.isStatusOK()) {
                    // Mined but reverted (status != 0x1): treat as a failure, do not confirm.
                    throw new IllegalStateException("recordAndReward receipt status not OK for session "
                            + session.getId());
                }
                // The receipt proves block inclusion -> confirm immediately and notify (US-39).
                String txHash = persist(session, receipt.getTransactionHash(), true);
                notifyTokensCredited(session);
                return Optional.of(txHash);
            } catch (Exception ex) {
                if (isAlreadyRecorded(ex)) {
                    // ERR-BC-02: another instance already minted. Treat as idempotent.
                    return handleAlreadyRecorded(session);
                }
                lastError = ex;
                LOGGER.warn("recordAndReward attempt {}/{} failed for session {}: {}",
                        attempt, MAX_ATTEMPTS, session.getId(), ex.getMessage());
                backoff(attempt);
            }
        }

        // ERR-BC-01 / ERR-BC-03: persistent failure. Do not break session confirmation;
        // off-chain points stay intact. Tx is left for reconciliation (no confirmed mark).
        LOGGER.error("recordAndReward failed after {} attempts for session {}; off-chain points intact",
                MAX_ATTEMPTS, session.getId(),
                lastError);
        return Optional.empty();
    }

    private Optional<String> handleAlreadyRecorded(RecyclingSession session) {
        var existing = txRepository.findBySessionId(session.getId());
        if (existing.isPresent()) {
            return Optional.of(existing.get().getTxHash());
        }
        // Session was recorded on-chain but we have no local record; upsert a placeholder.
        // The contract already registered it (anti double-spend), so it is effectively confirmed.
        String pseudoHash = "recorded:" + session.getId();
        LOGGER.info("Session {} already recorded on-chain; persisting confirmed reconciliation marker",
                session.getId());
        String txHash = persist(session, pseudoHash, true);
        notifyTokensCredited(session);
        return Optional.of(txHash);
    }

    private String persist(RecyclingSession session, String txHash, boolean confirmed) {
        var bcTx = new BlockchainTransaction(
                session.getId(),
                session.getUserId(),
                txHash,
                NETWORK,
                confirmed,
                "caps=" + session.getCapCount() + ";points=" + session.getPointsEarned());
        txRepository.save(bcTx);
        return txHash;
    }

    /**
     * Best-effort FCM: a failure here must never break the mint/confirmation flow.
     * Skipped when the session has no citizen yet ({@code userId == null}). The CTC
     * amount equals the off-chain {@code pointsEarned} (1 point = 1 CTC).
     */
    private void notifyTokensCredited(RecyclingSession session) {
        Long userId = session.getUserId();
        if (userId == null) {
            LOGGER.info("Session {} confirmed but has no userId — skipping FCM notification",
                    session.getId());
            return;
        }
        try {
            String body = "Se acreditaron " + session.getPointsEarned() + " CTC en tu wallet.";
            notificationContextFacade.notifyUser(userId, NOTIFICATION_TITLE, body);
        } catch (Exception ex) {
            // best-effort: confirmation already persisted; never propagate.
            LOGGER.warn("FCM notification failed for session {} (confirmation intact): {}",
                    session.getId(), ex.getMessage());
        }
    }

    private boolean isAlreadyRecorded(Exception ex) {
        String msg = ex.getMessage();
        return msg != null && msg.toLowerCase().contains("session already recorded");
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(BACKOFF_BASE_MILLIS * (1L << (attempt - 1)));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
