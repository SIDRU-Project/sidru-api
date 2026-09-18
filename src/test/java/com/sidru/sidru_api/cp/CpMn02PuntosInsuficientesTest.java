package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CpMn02 — US-MN-03, escenario "Puntos insuficientes": un ciudadano con 300 puntos que intenta
 * retirar 500 recibe 422 ERR_BC_005 y no se debita ni se toca el contrato.
 */
@DisplayName("CpMn02 - US-MN-03: puntos insuficientes")
class CpMn02PuntosInsuficientesTest extends CpBaseTest {

    private static final String VALID_ADDR = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";

    @MockitoBean
    private ChapaTuCriptoContract contract;

    @Test
    @DisplayName("300 puntos, retira 500: 422 ERR_BC_005 sin debitar ni tocar el contrato")
    void puntosInsuficientesRechazaConErrBc005() throws Exception {
        var citizen = newCitizen();
        givePoints(citizen, 300);

        mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", VALID_ADDR, "points", 500, "mode", "CTC"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ERR_BC_005"));

        mockMvc.perform(apiGet("/wallet/me").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsBalance").value(300));
        verifyNoInteractions(contract);
    }
}
