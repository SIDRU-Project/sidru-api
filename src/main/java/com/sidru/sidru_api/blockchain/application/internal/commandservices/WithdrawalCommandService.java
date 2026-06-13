package com.sidru.sidru_api.blockchain.application.internal.commandservices;

import com.sidru.sidru_api.blockchain.application.internal.custodial.CustodialWalletService;
import com.sidru.sidru_api.blockchain.domain.model.aggregates.WithdrawalRequest;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.InvalidWithdrawalAddressException;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.NoBalanceToWithdrawException;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.WithdrawalInProgressException;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.EvmAddress;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalStatus;
import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.WithdrawalRequestRepository;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.BlockchainProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.List;

/**
 * Idempotent custodial withdrawal: moves the full custodial CTC balance to the
 * citizen's external wallet via {@code withdrawTo} (backend pays gas, custodial
 * address never signs). Validates EIP-55, guards against concurrent withdrawals,
 * and persists status (EN_PROCESO/COMPLETADO/FALLIDO).
 */
@Service
public class WithdrawalCommandService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WithdrawalCommandService.class);

    private final CustodialWalletService custodialWalletService;
    private final ChapaTuCriptoContract contract;
    private final WithdrawalRequestRepository repository;
    private final BlockchainProperties properties;

    public WithdrawalCommandService(CustodialWalletService custodialWalletService,
                                    ChapaTuCriptoContract contract,
                                    WithdrawalRequestRepository repository,
                                    BlockchainProperties properties) {
        this.custodialWalletService = custodialWalletService;
        this.contract = contract;
        this.repository = repository;
        this.properties = properties;
    }

    public WithdrawalRequest withdraw(Long userId, String toAddress) {
        validateAddress(toAddress);

        // Idempotency: a withdrawal already in progress -> return it (no new tx).
        List<WithdrawalRequest> inProgress =
                repository.findByUserIdAndStatus(userId, WithdrawalStatus.EN_PROCESO);
        if (!inProgress.isEmpty()) {
            throw new WithdrawalInProgressException("Retiro en curso para el usuario " + userId);
        }

        String custodial = custodialWalletService.addressFor(userId);
        BigInteger balance = currentBalance(custodial);
        if (balance.signum() == 0) {
            throw new NoBalanceToWithdrawException("Sin saldo para retirar");
        }

        WithdrawalRequest request = repository.save(
                new WithdrawalRequest(userId, toAddress, balance.toString()));

        if (!properties.isEnabled()) {
            // Dev mode: cannot move tokens; mark FALLIDO without touching the network.
            LOGGER.warn("Blockchain disabled — withdrawal {} cannot be executed on-chain", request.getId());
            request.fail();
            return repository.save(request);
        }

        try {
            TransactionReceipt receipt = contract.withdrawTo(custodial, toAddress, balance);
            request.complete(receipt.getTransactionHash());
            LOGGER.info("Withdrawal {} completed, txHash={}", request.getId(), receipt.getTransactionHash());
        } catch (Exception ex) {
            LOGGER.error("Withdrawal {} failed: {}", request.getId(), ex.getMessage());
            request.fail();
        }
        return repository.save(request);
    }

    public WithdrawalRequest lastStatus(Long userId) {
        return repository.findTopByUserIdOrderByIdDesc(userId).orElse(null);
    }

    private BigInteger currentBalance(String custodial) {
        if (!properties.isEnabled()) {
            return BigInteger.ZERO;
        }
        try {
            return contract.balanceOf(custodial);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo consultar el saldo on-chain", ex);
        }
    }

    private void validateAddress(String toAddress) {
        if (!EvmAddress.isValid(toAddress)) {
            throw new InvalidWithdrawalAddressException("Dirección de retiro inválida (formato/checksum EIP-55)");
        }
    }
}
