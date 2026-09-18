package com.sidru.sidru_api.blockchain.application.internal.queryservices;

import com.sidru.sidru_api.blockchain.domain.model.aggregates.WithdrawalRequest;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalStatus;
import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.WithdrawalRequestRepository;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.BlockchainProperties;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.WithdrawalProperties;
import com.sidru.sidru_api.users.interfaces.acl.UserProfileContextFacade;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Read-side service for the citizen wallet (spec sidru-mainnet). Puntos son la unica fuente
 * del saldo: ya no hay direccion custodial ni balanceOf que consultar en la cadena.
 */
@Service
public class WalletQueryService {

    private static final BigDecimal POINTS_PER_SOL = new BigDecimal("100");

    private final UserProfileContextFacade userProfileContextFacade;
    private final WithdrawalRequestRepository withdrawalRepository;
    private final BlockchainProperties blockchainProperties;
    private final WithdrawalProperties withdrawalProperties;

    public WalletQueryService(UserProfileContextFacade userProfileContextFacade,
                              WithdrawalRequestRepository withdrawalRepository,
                              BlockchainProperties blockchainProperties,
                              WithdrawalProperties withdrawalProperties) {
        this.userProfileContextFacade = userProfileContextFacade;
        this.withdrawalRepository = withdrawalRepository;
        this.blockchainProperties = blockchainProperties;
        this.withdrawalProperties = withdrawalProperties;
    }

    public WalletSummaryView getWallet(Long userId) {
        int points = userProfileContextFacade.fetchTotalPointsByUserId(userId);
        BigDecimal soles = new BigDecimal(points)
                .divide(POINTS_PER_SOL, 2, RoundingMode.HALF_UP);

        String linkedWallet = withdrawalRepository
                .findTopByUserIdOrderByIdDesc(userId)
                .filter(w -> w.getStatus() == WithdrawalStatus.COMPLETADO)
                .map(WithdrawalRequest::getToAddress)
                .orElse(null);

        boolean hasInProgress = !withdrawalRepository
                .findByUserIdAndStatus(userId, WithdrawalStatus.EN_PROCESO)
                .isEmpty();

        return new WalletSummaryView(
                points,
                String.valueOf(points),
                soles.toPlainString(),
                blockchainProperties.getNetworkLabel(),
                blockchainProperties.getExplorerBaseUrl(),
                linkedWallet,
                withdrawalProperties.getMinPoints(),
                withdrawalProperties.isEnabled(),
                hasInProgress);
    }

    /** Historial de retiros del usuario, del mas reciente al mas antiguo. */
    public List<WithdrawalRequest> getWithdrawals(Long userId) {
        return withdrawalRepository.findAllByUserIdOrderByIdDesc(userId);
    }
}
