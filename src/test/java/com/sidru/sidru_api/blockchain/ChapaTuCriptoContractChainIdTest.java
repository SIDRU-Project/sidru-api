package com.sidru.sidru_api.blockchain;

import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthChainId;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El chainId con el que se firman las transacciones (EIP-155) se lee del nodo, nunca se fija en
 * el código (D12): el mismo build firma para Amoy (80002) en el ensayo y para Polygon mainnet
 * (137) en producción. Con el valor fijo en 80002, mainnet rechazaba cada envío con
 * "invalid chain id for signer: have 80002 want 137".
 */
class ChapaTuCriptoContractChainIdTest {

    @SuppressWarnings("unchecked")
    private static Web3j nodeAnswering(String hexChainId, String error) throws IOException {
        Web3j client = mock(Web3j.class);
        Request<?, EthChainId> request = mock(Request.class);
        EthChainId response = new EthChainId();
        if (error != null) {
            response.setError(new Response.Error(-32000, error));
        } else {
            response.setResult(hexChainId);
        }
        when(request.send()).thenReturn(response);
        when(client.ethChainId()).thenReturn((Request) request);
        return client;
    }

    @Test
    void mainnetDevuelve137() throws IOException {
        assertEquals(137L, ChapaTuCriptoContract.resolveChainId(nodeAnswering("0x89", null)));
    }

    @Test
    void amoyDevuelve80002() throws IOException {
        assertEquals(80002L, ChapaTuCriptoContract.resolveChainId(nodeAnswering("0x13882", null)));
    }

    @Test
    void unErrorDelNodoSePropagaComoIOException() throws IOException {
        Web3j client = nodeAnswering(null, "rpc down");
        IOException ex = assertThrows(IOException.class, () -> ChapaTuCriptoContract.resolveChainId(client));
        assertTrue(ex.getMessage().contains("rpc down"));
    }
}
