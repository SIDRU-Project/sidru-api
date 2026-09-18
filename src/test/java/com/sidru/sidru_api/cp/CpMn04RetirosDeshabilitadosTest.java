package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CpMn04 — design.md §9: con sidru.withdrawal.enabled=false el endpoint responde 503
 * ERR_BC_010 (p. ej. mientras se fondea la reserva antes del smoke test de mainnet).
 */
@TestPropertySource(properties = "sidru.withdrawal.enabled=false")
@DisplayName("CpMn04 - Retiros deshabilitados (sidru.withdrawal.enabled=false)")
class CpMn04RetirosDeshabilitadosTest extends CpBaseTest {

    private static final String VALID_ADDR = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";

    @MockitoBean
    private ChapaTuCriptoContract contract;

    @Test
    @DisplayName("Con los retiros deshabilitados responde 503 ERR_BC_010 sin debitar ni tocar el contrato")
    void conRetirosDeshabilitadosResponde503() throws Exception {
        var citizen = newCitizen();
        givePoints(citizen, 1000);

        mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", VALID_ADDR, "points", 500, "mode", "CTC"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ERR_BC_010"));

        mockMvc.perform(apiGet("/wallet/me").header("Authorization", citizen.bearer()))
                .andExpect(jsonPath("$.pointsBalance").value(1000))
                .andExpect(jsonPath("$.withdrawalsEnabled").value(false));
        verifyNoInteractions(contract);
    }
}
