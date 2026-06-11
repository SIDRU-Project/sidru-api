package com.sidru.sidru_api.blockchain.application.internal.queryservices;

import com.sidru.sidru_api.blockchain.application.internal.custodial.CustodialWalletService;
import com.sidru.sidru_api.blockchain.domain.model.aggregates.WithdrawalRequest;
import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.BlockchainTransactionRepository;
import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.WithdrawalRequestRepository;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.BlockchainProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-side service for the citizen wallet: custodial address, on-chain balance
 * ({@code balanceOf}) and transaction history derived from {@link com.sidru.sidru_api.blockchain.domain.model.aggregates.BlockchainTransaction}.
 */
@Service
public class WalletQueryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WalletQueryService.class);
    private static final String NETWORK = "polygon-amoy";
    private static final String EXPLORER_TX = "https://amoy.polygonscan.com/tx/";
    private static final BigDecimal WEI_PER_CTC = BigDecimal.TEN.pow(18);
    // points-per-sol = 100 -> 1 CTC ≈ S/ 0.01 (referential).
    private static final BigDecimal POINTS_PER_SOL = new BigDecimal("100");

    private final CustodialWalletService custodialWalletService;
    private final ChapaTuCriptoContract contract;
    private final BlockchainTransactionRepository txRepository;
    private final WithdrawalRequestRepository withdrawalRepository;
    private final BlockchainProperties properties;

    public WalletQueryService(CustodialWalletService custodialWalletService,
                              ChapaTuCriptoContract contract,
                              BlockchainTransactionRepository txRepository,
                              WithdrawalRequestRepository withdrawalRepository,
                              BlockchainProperties properties) {
        this.custodialWalletService = custodialWalletService;
        this.contract = contract;
        this.txRepository = txRepository;
        this.withdrawalRepository = withdrawalRepository;
        this.properties = properties;
    }

    public WalletView getWallet(Long userId) {
        String address = custodialWalletService.addressFor(userId);
        BigInteger balanceWei = balanceOf(address);

        BigDecimal balanceCtc = new BigDecimal(balanceWei).divide(WEI_PER_CTC);
        BigDecimal solesRef = balanceCtc.divide(POINTS_PER_SOL, 2, RoundingMode.HALF_UP);

        String linkedWallet = withdrawalRepository.findTopByUserIdOrderByIdDesc(userId)
                .map(WithdrawalRequest::getToAddress)
                .orElse(null);

        return new WalletView(
                address,
                NETWORK,
                balanceWei.toString(),
                balanceCtc.toPlainString(),
                solesRef.toPlainString(),
                linkedWallet);
    }

    public List<WalletTransactionView> getTransactions(Long userId) {
        List<WalletTransactionView> result = new ArrayList<>();

        txRepository.findByUserIdOrderByIdDesc(userId).forEach(tx ->
                result.add(new WalletTransactionView(
                        "MINT",
                        tx.getTxHash(),
                        tx.isConfirmed() ? "CONFIRMED" : "PENDING",
                        EXPLORER_TX + tx.getTxHash())));

        withdrawalRepository.findTopByUserIdOrderByIdDesc(userId).ifPresent(w -> {
            if (w.getTxHash() != null) {
                result.add(new WalletTransactionView(
                        "WITHDRAW",
                        w.getTxHash(),
                        w.getStatus().name(),
                        EXPLORER_TX + w.getTxHash()));
            }
        });

        return result;
    }

    private BigInteger balanceOf(String address) {
        if (!properties.isEnabled()) {
            // Dev mode without blockchain: report zero balance, never touch the network.
            return BigInteger.ZERO;
        }
        try {
            return contract.balanceOf(address);
        } catch (Exception ex) {
            LOGGER.error("balanceOf failed for {}: {}", address, ex.getMessage());
            throw new IllegalStateException("No se pudo consultar el saldo on-chain", ex);
        }
    }
}
