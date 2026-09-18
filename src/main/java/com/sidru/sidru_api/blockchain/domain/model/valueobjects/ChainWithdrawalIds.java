package com.sidru.sidru_api.blockchain.domain.model.valueobjects;

import java.security.SecureRandom;

/**
 * Genera el withdrawalId que viaja a mintWithdrawal/payoutReserve: aleatorio y único,
 * independiente de la secuencia de BD de {@code WithdrawalRequest}. Dos bases de datos
 * distintas contra el mismo contrato (local y VM, o una restauración) no deben poder
 * coincidir en este id (RN-BC-07).
 */
public final class ChainWithdrawalIds {

    private static final SecureRandom RANDOM = new SecureRandom();

    private ChainWithdrawalIds() {}

    public static long next() {
        long id;
        do {
            id = RANDOM.nextLong() & Long.MAX_VALUE;
        } while (id == 0);
        return id;
    }
}
