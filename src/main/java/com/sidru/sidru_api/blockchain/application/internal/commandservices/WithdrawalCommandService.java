package com.sidru.sidru_api.blockchain.application.internal.commandservices;

import com.sidru.sidru_api.blockchain.domain.model.aggregates.WithdrawalRequest;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.BelowMinimumWithdrawalException;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.InvalidWithdrawalAddressException;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.NoBalanceToWithdrawException;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.WithdrawalInProgressException;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.WithdrawalRequestNotFoundException;
import com.sidru.sidru_api.blockchain.domain.model.exceptions.WithdrawalsDisabledException;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.EvmAddress;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalMode;
import com.sidru.sidru_api.blockchain.domain.model.valueobjects.WithdrawalStatus;
import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.WithdrawalRequestRepository;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.ContractRevertException;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.WithdrawalProperties;
import com.sidru.sidru_api.users.domain.model.exceptions.InsufficientPointsException;
import com.sidru.sidru_api.users.interfaces.acl.UserProfileContextFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;

/**
 * Retiro de puntos a CTC (mint directo a la wallet del ciudadano) o USDC (pago desde la
 * reserva a la par). Débito primero, cadena después: el guard + débito + persistencia van
 * en una transacción (design.md §5); el envío on-chain corre fuera de ella, vía
 * {@link TransactionTemplate} en vez de {@code @Transactional} para evitar el problema de
 * auto-invocación (withdraw() y el paso transaccional viven en el mismo bean).
 */
@Service
public class WithdrawalCommandService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WithdrawalCommandService.class);
    private static final BigInteger WEI_PER_CTC = BigInteger.TEN.pow(18);

    private final UserProfileContextFacade userProfileContextFacade;
    private final ChapaTuCriptoContract contract;
    private final WithdrawalRequestRepository repository;
    private final WithdrawalProperties withdrawalProperties;
    private final WithdrawalNotifier notifier;
    private final TransactionTemplate transactionTemplate;

    public WithdrawalCommandService(UserProfileContextFacade userProfileContextFacade,
                                    ChapaTuCriptoContract contract,
                                    WithdrawalRequestRepository repository,
                                    WithdrawalProperties withdrawalProperties,
                                    WithdrawalNotifier notifier,
                                    PlatformTransactionManager transactionManager) {
        this.userProfileContextFacade = userProfileContextFacade;
        this.contract = contract;
        this.repository = repository;
        this.withdrawalProperties = withdrawalProperties;
        this.notifier = notifier;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Inicia un retiro. Devuelve el {@link WithdrawalRequest} en EN_PROCESO (202) o ya
     * COMPLETADO (200) si la transacción confirmó dentro de la misma petición.
     */
    public WithdrawalRequest withdraw(Long userId, String toAddress, int points, WithdrawalMode mode) {
        if (!withdrawalProperties.isEnabled()) {
            throw new WithdrawalsDisabledException(
                    "Los retiros están temporalmente deshabilitados");
        }
        if (!EvmAddress.isValid(toAddress)) {
            throw new InvalidWithdrawalAddressException(
                    "Dirección de retiro inválida (formato/checksum EIP-55)");
        }
        if (points < withdrawalProperties.getMinPoints()) {
            throw new BelowMinimumWithdrawalException(
                    "El retiro mínimo es de " + withdrawalProperties.getMinPoints() + " puntos");
        }

        WithdrawalRequest request = createAndDebit(userId, toAddress, points, mode);
        submit(request);
        return repository.findById(request.getId()).orElse(request);
    }

    public WithdrawalRequest lastStatus(Long userId) {
        return repository.findTopByUserIdOrderByIdDesc(userId).orElse(null);
    }

    /** GET /wallet/withdraw/{id}: 404 si no existe o no pertenece al usuario. */
    public WithdrawalRequest getById(Long id, Long userId) {
        WithdrawalRequest request = repository.findById(id)
                .orElseThrow(WithdrawalRequestNotFoundException::new);
        if (!request.getUserId().equals(userId)) {
            throw new WithdrawalRequestNotFoundException();
        }
        return request;
    }

    /**
     * Guard "un retiro a la vez" + débito bajo lock pesimista + persistencia, en una única
     * transacción (design.md §5, paso 2).
     */
    private WithdrawalRequest createAndDebit(Long userId, String toAddress, int points, WithdrawalMode mode) {
        return transactionTemplate.execute(status -> {
            // El lock se toma ANTES del guard, no después: si el guard corriera primero, dos
            // peticiones concurrentes lo pasarían ambas y solo se serializarían al debitar,
            // debitando las dos. Al debitar primero, la segunda espera el lock del perfil y,
            // ya con él, lee el EN_PROCESO que la primera dejó COMMITEADO (READ COMMITTED).
            try {
                userProfileContextFacade.subtractPointsLocked(userId, points);
            } catch (InsufficientPointsException ex) {
                throw new NoBalanceToWithdrawException("Puntos insuficientes para retirar");
            }
            if (!repository.findByUserIdAndStatus(userId, WithdrawalStatus.EN_PROCESO).isEmpty()) {
                // El rollback del template deshace el débito de arriba automáticamente.
                throw new WithdrawalInProgressException("Ya hay un retiro en proceso para este usuario");
            }
            BigInteger amountWei = BigInteger.valueOf(points).multiply(WEI_PER_CTC);
            return repository.save(new WithdrawalRequest(userId, toAddress, points, amountWei.toString(), mode));
        });
    }

    /**
     * Envía (o reintenta) la transacción on-chain de un retiro. Nunca corre dentro de una
     * transacción de base de datos: cada `save` aquí es su propio commit corto. Reutilizable
     * por {@code WithdrawalReconciliationService} (design.md §5, paso 3).
     */
    public void submit(WithdrawalRequest request) {
        BigInteger withdrawalId = BigInteger.valueOf(request.getChainWithdrawalId());
        BigInteger amount = new BigInteger(request.getAmountWei());
        try {
            TransactionReceipt receipt = request.getMode() == WithdrawalMode.CTC
                    ? contract.mintWithdrawal(request.getToAddress(), withdrawalId, amount)
                    : contract.payoutReserve(request.getToAddress(), withdrawalId, amount);
            request.markAttempt();
            if (request.getMode() == WithdrawalMode.USDC) {
                BigInteger reserveOut = contract.decodeReserveOut(receipt);
                if (reserveOut != null) {
                    request.setReserveOut(reserveOut.toString());
                }
            }
            request.complete(receipt.getTransactionHash());
            notifier.notifyCompleted(request);
        } catch (Exception ex) {
            if (contract.isWithdrawalAlreadyProcessed(ex)) {
                request.markAttempt();
                request.completeFromChain(request.getChainWithdrawalId());
                notifier.notifyCompleted(request);
            } else if (contract.isInsufficientReserve(ex)) {
                // Regla explícita (design.md §4): no consume el presupuesto de intentos ni
                // dispara refund. Es un problema del operador (recargar reserva), no del retiro.
                LOGGER.warn("Reserva insuficiente para completar el retiro {} (chainId {}): {}",
                        request.getId(), request.getChainWithdrawalId(), ex.getMessage());
            } else if (ex instanceof ContractRevertException cre && cre.getRevertReason() == null) {
                // No pudimos obtener el motivo del revert (el nodo no devolvió revert data).
                // Podría ser InsufficientReserve sin dato: cerrar en falso un retiro que solo
                // esperaba recarga sería peor que reintentar. La reconciliación lo resuelve
                // via withdrawalProcessed(id) o lo lleva a FALLIDO al agotar los intentos.
                request.markAttempt();
                LOGGER.warn("Revert sin motivo disponible para el retiro {} (chainId {}), se reintentará",
                        request.getId(), request.getChainWithdrawalId());
            } else if (ex instanceof ContractRevertException) {
                // Revert con motivo distinto de los dos selectores conocidos: determinista, no
                // tiene sentido reintentar (el mismo estado on-chain revierte siempre igual).
                request.markAttempt();
                request.fail(ex.getMessage());
                userProfileContextFacade.refundPoints(request.getUserId(), request.getPoints());
                request.markRefunded();
                notifier.notifyFailed(request);
            } else {
                // RPC/red/timeout: reintentable, la reconciliación lo retoma.
                request.markAttempt();
                LOGGER.warn("Envío on-chain del retiro {} (chainId {}) falló, se reintentará: {}",
                        request.getId(), request.getChainWithdrawalId(), ex.getMessage());
            }
        }
        repository.save(request);
    }
}
