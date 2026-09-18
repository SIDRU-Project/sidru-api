package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.ContractRevertException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CpMn06 — design.md §4: un revert determinista (con motivo, distinto de los dos selectores
 * conocidos) deja el retiro FALLIDO y devuelve los puntos dentro de la misma petición. Por la
 * corrección D (Tarea 3b), un resultado final (FALLIDO incluido) responde 200, no 202.
 */
@DisplayName("CpMn06 - Revert determinista: 200 FALLIDO con refund")
class CpMn06RevertDeterministaConRefundTest extends CpBaseTest {

    private static final String VALID_ADDR = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";

    @MockitoBean
    private ChapaTuCriptoContract contract;

    @Test
    @DisplayName("Un revert determinista responde 200 con status FALLIDO y los puntos vuelven en la misma peticion")
    void revertDeterministaRespondeFalladoYDevuelveLosPuntos() throws Exception {
        var citizen = newCitizen();
        givePoints(citizen, 800);
        // Motivo no nulo y distinto de los selectores de WithdrawalAlreadyProcessed/InsufficientReserve:
        // isWithdrawalAlreadyProcessed/isInsufficientReserve del mock devuelven false por defecto,
        // exactamente como la clasificacion real ante un selector desconocido.
        when(contract.mintWithdrawal(any(), any(), any()))
                .thenThrow(new ContractRevertException("0xotroErrorDelContrato"));

        mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", VALID_ADDR, "points", 800, "mode", "CTC"))))
                .andExpect(status().isOk()) // 200: resultado final, no EN_PROCESO (correccion D)
                .andExpect(jsonPath("$.status").value("FALLIDO"))
                .andExpect(jsonPath("$.failureReason").isNotEmpty());

        mockMvc.perform(apiGet("/wallet/me").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsBalance").value(800));
    }
}
