package com.sidru.sidru_api.blockchain.application.internal.eventhandlers;

import com.sidru.sidru_api.blockchain.domain.model.aggregates.BlockchainTransaction;
import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.BlockchainTransactionRepository;
import com.sidru.sidru_api.notifications.interfaces.acl.NotificationContextFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

/**
 * Application service that reacts to the on-chain {@code TokensMinted} event (US-BC-07 / US-39).
 *
 * <p>Pure, chain-agnostic unit: it receives already-decoded values from the infrastructure
 * listener and (1) confirms the matching {@link BlockchainTransaction}, (2) fires a best-effort
 * FCM notification via the notifications ACL. It is idempotent: an already-confirmed transaction
 * is neither re-saved nor re-notified, so duplicate/replayed events are harmless (RNF-BC-07).
 *
 * <p>Decoupling: notifications are reached through {@link NotificationContextFacade} (the ACL),
 * not the internal notifications port.
 */
@Service
public class TokensMintedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokensMintedHandler.class);
    private static final BigDecimal WEI_PER_CTC = BigDecimal.TEN.pow(18);
    private static final String NOTIFICATION_TITLE = "¡Tokens CTC acreditados!";

    private final BlockchainTransactionRepository txRepository;
    private final NotificationContextFacade notificationContextFacade;

    public TokensMintedHandler(BlockchainTransactionRepository txRepository,
                               NotificationContextFacade notificationContextFacade) {
        this.txRepository = txRepository;
        this.notificationContextFacade = notificationContextFacade;
    }

    /**
     * Handles a decoded TokensMinted event.
     *
     * @param sessionId the recycling session id (from the indexed topic)
     * @param user      the custodial address that received the tokens (from the indexed topic)
     * @param amount    minted amount in wei (from the event data)
     */
    @Transactional
    public void onTokensMinted(Long sessionId, String user, BigInteger amount) {
        if (sessionId == null) {
            LOGGER.warn("TokensMinted received with null sessionId — ignored");
            return;
        }

        Optional<BlockchainTransaction> maybeTx = txRepository.findBySessionId(sessionId);
        if (maybeTx.isEmpty()) {
            // No local record (e.g. event from another instance / reconciliation pending).
            LOGGER.info("TokensMinted for session {} has no local BlockchainTransaction — skipping", sessionId);
            return;
        }

        BlockchainTransaction tx = maybeTx.get();
        if (tx.isConfirmed()) {
            // Idempotency: already confirmed and notified -> do nothing.
            LOGGER.debug("Session {} already confirmed — ignoring duplicate TokensMinted", sessionId);
            return;
        }

        tx.setConfirmed(true);
        txRepository.save(tx);
        LOGGER.info("Session {} confirmed on-chain (tokens minted to custodial address)", sessionId);

        Long userId = tx.getUserId();
        if (userId == null) {
            LOGGER.info("Session {} confirmed but has no userId — skipping FCM notification", sessionId);
            return;
        }

        String body = "Se acreditaron " + formatCtc(amount) + " CTC en tu wallet.";
        notificationContextFacade.notifyUser(userId, NOTIFICATION_TITLE, body);
    }

    /** Formats wei amount to a human-readable CTC value (18 decimals), trimming trailing zeros. */
    private String formatCtc(BigInteger amountWei) {
        if (amountWei == null) {
            return "0";
        }
        return new BigDecimal(amountWei)
                .divide(WEI_PER_CTC)
                .stripTrailingZeros()
                .toPlainString();
    }
}
