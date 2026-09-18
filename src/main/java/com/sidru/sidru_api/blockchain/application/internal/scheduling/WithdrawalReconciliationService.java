package com.sidru.sidru_api.blockchain.application.internal.scheduling;

import com.sidru.sidru_api.blockchain.application.internal.commandservices.WithdrawalCommandService;
import com.sidru.sidru_api.blockchain.application.internal.commandservices.WithdrawalNotifier;
import com.sidru.sidru_api.blockchain.domain.model.aggregates.WithdrawalRequest;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalStatus;
import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.WithdrawalRequestRepository;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.WithdrawalProperties;
import com.sidru.sidru_api.users.interfaces.acl.UserProfileContextFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cierra los retiros EN_PROCESO ambiguos (design.md §5). Solo se activa con
 * sidru.blockchain.enabled=true: sin cadena real no hay nada que reconciliar.
 */
@Service
@ConditionalOnProperty(value = "sidru.blockchain.enabled", havingValue = "true")
public class WithdrawalReconciliationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WithdrawalReconciliationService.class);

    private final WithdrawalRequestRepository repository;
    private final ChapaTuCriptoContract contract;
    private final WithdrawalCommandService withdrawalCommandService;
    private final WithdrawalProperties withdrawalProperties;
    private final UserProfileContextFacade userProfileContextFacade;
    private final WithdrawalNotifier notifier;

    public WithdrawalReconciliationService(WithdrawalRequestRepository repository,
                                           ChapaTuCriptoContract contract,
                                           WithdrawalCommandService withdrawalCommandService,
                                           WithdrawalProperties withdrawalProperties,
                                           UserProfileContextFacade userProfileContextFacade,
                                           WithdrawalNotifier notifier) {
        this.repository = repository;
        this.contract = contract;
        this.withdrawalCommandService = withdrawalCommandService;
        this.withdrawalProperties = withdrawalProperties;
        this.userProfileContextFacade = userProfileContextFacade;
        this.notifier = notifier;
    }

    @Scheduled(fixedDelayString = "${sidru.withdrawal.reconciliation.interval-ms}")
    public void reconcile() {
        LocalDateTime threshold = LocalDateTime.now()
                .minusSeconds(withdrawalProperties.getReconciliation().getGraceSeconds());
        List<WithdrawalRequest> candidates = repository
                .findTop50ByStatusAndUpdatedAtLessThanEqualOrderByIdAsc(WithdrawalStatus.EN_PROCESO, threshold);
        for (WithdrawalRequest request : candidates) {
            try {
                reconcileOne(request);
            } catch (Exception ex) {
                // Una fila rota no debe cortar el tick para las demas.
                LOGGER.error("Reconciliación del retiro {} falló: {}", request.getId(), ex.getMessage());
            }
        }
    }

    private void reconcileOne(WithdrawalRequest request) {
        Long chainWithdrawalId = request.getChainWithdrawalId();
        if (chainWithdrawalId == null) {
            // Fila anterior a este campo: ni hay id de cadena que consultar ni uno que reenviar
            // (reenviar reventaria con NPE al construir el BigInteger). Requiere intervencion manual.
            LOGGER.warn("Retiro {} sin chainWithdrawalId: requiere intervención manual", request.getId());
            return;
        }

        if (isAlreadyProcessedOnChain(chainWithdrawalId)) {
            request.completeFromChain(chainWithdrawalId);
            repository.save(request);
            notifier.notifyCompleted(request);
            return;
        }

        if (request.getAttempts() < withdrawalProperties.getMaxAttempts()) {
            withdrawalCommandService.submit(request);
        } else {
            request.fail("max attempts");
            userProfileContextFacade.refundPoints(request.getUserId(), request.getPoints());
            request.markRefunded();
            repository.save(request);
            notifier.notifyFailed(request);
        }
    }

    private boolean isAlreadyProcessedOnChain(long chainWithdrawalId) {
        try {
            return contract.withdrawalProcessed(BigInteger.valueOf(chainWithdrawalId));
        } catch (Exception ex) {
            LOGGER.warn("No se pudo consultar withdrawalProcessed({}): {}",
                    chainWithdrawalId, ex.getMessage());
            return false;
        }
    }
}
