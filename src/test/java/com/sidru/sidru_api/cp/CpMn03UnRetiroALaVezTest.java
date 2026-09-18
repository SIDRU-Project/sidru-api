package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CpMn03 — US-MN-03, escenario "Un retiro a la vez" (secuencial; la variante concurrente real
 * está en {@link CpMn08RetiroConcurrenciaTest}): con un retiro ya EN_PROCESO, un segundo intento
 * responde 409 ERR_BC_007.
 */
@DisplayName("CpMn03 - US-MN-03: un retiro a la vez")
class CpMn03UnRetiroALaVezTest extends CpBaseTest {

    private static final String VALID_ADDR = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";

    @MockitoBean
    private ChapaTuCriptoContract contract;

    @Test
    @DisplayName("Con un retiro EN_PROCESO, un segundo intento responde 409 ERR_BC_007")
    void segundoIntentoConUnoEnProcesoRespondeConflicto() throws Exception {
        var citizen = newCitizen();
        givePoints(citizen, 1500);
        when(contract.mintWithdrawal(any(), any(), any())).thenThrow(new IOException("rpc down"));

        mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", VALID_ADDR, "points", 500, "mode", "CTC"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("EN_PROCESO"));

        mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", VALID_ADDR, "points", 500, "mode", "CTC"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_BC_007"));

        // Solo el primer retiro debito puntos; el segundo no.
        mockMvc.perform(apiGet("/wallet/me").header("Authorization", citizen.bearer()))
                .andExpect(jsonPath("$.pointsBalance").value(1000));
    }
}
