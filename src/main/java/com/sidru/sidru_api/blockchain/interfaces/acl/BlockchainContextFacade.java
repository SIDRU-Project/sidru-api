package com.sidru.sidru_api.blockchain.interfaces.acl;

import java.time.LocalDateTime;

/**
 * ACL de lectura del contexto {@code blockchain}: deja que otros contextos (p. ej.
 * {@code metrics}) lean indicadores de retiros sin acoplarse a {@code WithdrawalRequest}
 * ni a su repositorio.
 */
public interface BlockchainContextFacade {

    /** Suma de puntos de retiros COMPLETADO creados en [from, to]. CTC efectivamente retirados. */
    long sumCompletedWithdrawalCtc(LocalDateTime from, LocalDateTime to);
}
