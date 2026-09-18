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
 * CpMn01 — US-MN-03, escenario "Mínimo de retiro": por debajo de sidru.withdrawal.min-points
 * (500 en el perfil de test) la petición se rechaza con ERR_BC_009 y no se debita ningún
 * punto ni se toca el contrato.
 */
@DisplayName("CpMn01 - US-MN-03: minimo de retiro")
class CpMn01RetiroMinimoTest extends CpBaseTest {

    private static final String VALID_ADDR = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";

    @MockitoBean
    private ChapaTuCriptoContract contract;

    @Test
    @DisplayName("Retirar por debajo del minimo responde 400 ERR_BC_009 sin debitar puntos ni enviar transaccion")
    void bajoElMinimoRechazaSinDebitarNiTocarElContrato() throws Exception {
        var citizen = newCitizen();
        givePoints(citizen, 1000);

        mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", VALID_ADDR, "points", 499, "mode", "CTC"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_BC_009"))
                .andExpect(jsonPath("$.details", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("500"))));

        mockMvc.perform(apiGet("/wallet/me").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsBalance").value(1000));
        verifyNoInteractions(contract);
    }
}
