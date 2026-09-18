package com.sidru.sidru_api.blockchain.infrastructure.web3j;

/**
 * A transaction was mined but reverted. Carries the raw revert data (hex, possibly a custom
 * error selector + ABI-encoded args) so callers can classify it — see
 * {@link ChapaTuCriptoContract#isWithdrawalAlreadyProcessed(Exception)} and
 * {@link ChapaTuCriptoContract#isInsufficientReserve(Exception)}.
 *
 * <p>NOTA (spec sidru-mainnet, Fase 3): design.md §5 asume que ya existe un mecanismo de
 * decodificacion de revertReason ("el mismo patron que hoy usa isAlreadyRecorded"), pero ese
 * metodo no existe en el codigo previo — {@code sendTransaction} nunca comprobaba
 * {@code receipt.isStatusOK()}. Esta clase y la logica en {@link ChapaTuCriptoContract} son
 * una implementacion nueva para cerrar ese hueco; revisarla con cuidado en la Fase 4
 * ({@code ChapaTuCriptoContractErrorsTest}).
 */
public class ContractRevertException extends RuntimeException {

    /** Raw revert data as returned by the node (hex string), or null if it could not be fetched. */
    private final String revertReason;

    public ContractRevertException(String revertReason) {
        super("Transacción revertida" + (revertReason != null ? ": " + revertReason : " (motivo no disponible)"));
        this.revertReason = revertReason;
    }

    public String getRevertReason() {
        return revertReason;
    }
}
