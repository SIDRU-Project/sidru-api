package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CpMn05 — US-MN-01, escenario "Retiro parcial": un ciudadano con 1200 puntos que retira 700
 * en CTC queda con 500 puntos disponibles y se mintean exactamente 700 CTC (700 x 10^18 wei).
 */
@DisplayName("CpMn05 - US-MN-01: retiro parcial")
class CpMn05RetiroParcialTest extends CpBaseTest {

    private static final String VALID_ADDR = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";

    @MockitoBean
    private ChapaTuCriptoContract contract;

    @Test
    @DisplayName("1200 puntos, retira 700: quedan 500 puntos y se mintean exactamente 700 CTC")
    void retiraParteDelSaldoYQuedaElRestoDisponible() throws Exception {
        var citizen = newCitizen();
        givePoints(citizen, 1200);

        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getTransactionHash()).thenReturn("0xparcial");
        when(contract.mintWithdrawal(eq(VALID_ADDR), any(),
                eq(BigInteger.valueOf(700).multiply(BigInteger.TEN.pow(18)))))
                .thenReturn(receipt);

        mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", VALID_ADDR, "points", 700, "mode", "CTC"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETADO"))
                .andExpect(jsonPath("$.amountWei").value("700000000000000000000"));

        mockMvc.perform(apiGet("/wallet/me").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsBalance").value(500));
    }
}
