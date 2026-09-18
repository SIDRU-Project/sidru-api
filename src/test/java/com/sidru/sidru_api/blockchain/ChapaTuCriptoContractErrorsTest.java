package com.sidru.sidru_api.blockchain;

import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.ContractRevertException;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.BlockchainProperties;
import org.junit.jupiter.api.Test;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Hash;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Clasificación de reverts (isWithdrawalAlreadyProcessed / isInsufficientReserve, contract-spec.md
 * §7) y decodificación de ReservePaidOut (decodeReserveOut). Sin nodo real: aquí se prueba la
 * clasificación de un {@link ContractRevertException} ya construido y la decodificación de un
 * {@link TransactionReceipt} armado a mano, no la red (design.md §7 / Tarea 3 punto 1: el
 * mecanismo de decodeReason es una implementación nueva de esta spec, no existía antes).
 */
class ChapaTuCriptoContractErrorsTest {

    // Mismas firmas que contract-spec.md §3; el selector es keccak256(firma)[:4].
    private static final String WITHDRAWAL_ALREADY_PROCESSED_SELECTOR =
            selector("WithdrawalAlreadyProcessed(uint256)");
    private static final String INSUFFICIENT_RESERVE_SELECTOR =
            selector("InsufficientReserve(uint256,uint256)");

    private static String selector(String errorSignature) {
        return Hash.sha3String(errorSignature).substring(0, 10);
    }

    private final ChapaTuCriptoContract contract = new ChapaTuCriptoContract(new BlockchainProperties());

    // ------------------------------------------------------ isWithdrawalAlreadyProcessed

    @Test
    void reconoceElSelectorExacto() {
        var ex = new ContractRevertException(WITHDRAWAL_ALREADY_PROCESSED_SELECTOR);

        assertTrue(contract.isWithdrawalAlreadyProcessed(ex));
        assertFalse(contract.isInsufficientReserve(ex));
    }

    @Test
    void reconoceElSelectorConArgumentosAbiCodificados() {
        // selector + withdrawalId=42 codificado como uint256 (64 hex, cero-rellenado).
        String argsEncoded = TypeEncoder.encode(new Uint256(BigInteger.valueOf(42L)));
        var ex = new ContractRevertException(INSUFFICIENT_RESERVE_SELECTOR + argsEncoded);

        assertTrue(contract.isInsufficientReserve(ex));
        assertFalse(contract.isWithdrawalAlreadyProcessed(ex));
    }

    @Test
    void reconoceElSelectorEnvueltoEnTextoDeErrorJsonRpc() {
        // fetchRevertReason puede devolver Object.toString() de un mapa de error JSON-RPC.
        String wrapped = "{code=3, message=execution reverted, data="
                + WITHDRAWAL_ALREADY_PROCESSED_SELECTOR + "0000000000000000000000000000000000000000000000000000000000000001}";
        var ex = new ContractRevertException(wrapped);

        assertTrue(contract.isWithdrawalAlreadyProcessed(ex));
    }

    @Test
    void revertReasonNuloNoCoincideConNingunSelector() {
        var ex = new ContractRevertException(null);

        assertFalse(contract.isWithdrawalAlreadyProcessed(ex));
        assertFalse(contract.isInsufficientReserve(ex));
    }

    @Test
    void unaExcepcionQueNoEsContractRevertNuncaCoincide() {
        Exception ex = new java.io.IOException("rpc down");

        assertFalse(contract.isWithdrawalAlreadyProcessed(ex));
        assertFalse(contract.isInsufficientReserve(ex));
    }

    // ------------------------------------------------------------ decodeReserveOut

    private static final Event RESERVE_PAID_OUT_EVENT = new Event(
            "ReservePaidOut",
            Arrays.asList(
                    new TypeReference<Address>(true) {},
                    new TypeReference<Uint256>(true) {},
                    new TypeReference<Uint256>(false) {},
                    new TypeReference<Uint256>(false) {}));

    @Test
    void decodeReserveOutLeeElSegundoParametroNoIndexadoDelEvento() {
        BigInteger ctcAmount = BigInteger.valueOf(1000L).multiply(BigInteger.TEN.pow(18));
        BigInteger reserveOut = BigInteger.valueOf(2_777_777L);

        Log log = new Log();
        log.setTopics(List.of(
                EventEncoder.encode(RESERVE_PAID_OUT_EVENT),
                "0x000000000000000000000000000000000000000000000000000000000000dead", // to (indexado, valor irrelevante aqui)
                "0x000000000000000000000000000000000000000000000000000000000000002a" // withdrawalId=42 (indexado)
        ));
        log.setData("0x" + TypeEncoder.encode(new Uint256(ctcAmount)) + TypeEncoder.encode(new Uint256(reserveOut)));

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setLogs(List.of(log));

        assertEquals(reserveOut, contract.decodeReserveOut(receipt));
    }

    @Test
    void decodeReserveOutDevuelveNullSiElReciboNoTraeElEvento() {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setLogs(List.of());

        assertNull(contract.decodeReserveOut(receipt));
    }
}
