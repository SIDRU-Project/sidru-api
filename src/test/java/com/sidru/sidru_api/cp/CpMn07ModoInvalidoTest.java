package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CpMn07 — US-MN-03, escenario "Modo obligatorio": un {@code mode} distinto de CTC/USDC (o
 * ausente) responde 400, sin exigir un codigo de error concreto (la spec solo pide el status).
 */
@DisplayName("CpMn07 - US-MN-03: modo obligatorio")
class CpMn07ModoInvalidoTest extends CpBaseTest {

    private static final String VALID_ADDR = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";

    @MockitoBean
    private ChapaTuCriptoContract contract;

    @Test
    @DisplayName("mode distinto de CTC/USDC responde 400")
    void modoDesconocidoResponde400() throws Exception {
        var citizen = newCitizen();
        givePoints(citizen, 1000);

        mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", VALID_ADDR, "points", 500, "mode", "EUR"))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(contract);
    }

    @Test
    @DisplayName("mode ausente responde 400")
    void modoAusenteResponde400() throws Exception {
        var citizen = newCitizen();
        givePoints(citizen, 1000);

        var body = new java.util.HashMap<String, Object>();
        body.put("toAddress", VALID_ADDR);
        body.put("points", 500);
        // sin "mode"

        mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(contract);
    }
}
