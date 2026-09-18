package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.BlockchainProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP019 — Saldo y equivalencias de la wallet (US-MN-05: el saldo deja de ser balanceOf, es
 * puntos). CP020 — Retiro con el cuerpo nuevo {@code {toAddress, points, mode}} (US-MN-01/02).
 * CP021 — Dirección inválida no debita ni toca el contrato (US-MN-03), sin cambio de fondo.
 *
 * <p>Reemplaza a Cp019Cp020Cp021WalletCustodialTest (modelo custodial, eliminado en la Fase 3
 * de la spec sidru-mainnet).</p>
 */
@TestPropertySource(properties = {
        "sidru.blockchain.network-label=polygon",
        "sidru.blockchain.explorer-base-url=https://polygonscan.com"
})
@DisplayName("CP019/CP020/CP021 - Wallet: saldo, retiro y validacion de direccion")
class Cp019Cp020Cp021WalletTest extends CpBaseTest {

    private static final String VALID_ADDR = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";
    private static final String CHECKSUM_INVALIDO = "0x5aAeb6053f3E94C9b9A09f33669435E7Ef1BeAed";
    private static final BigInteger CTC_UNIT = BigInteger.TEN.pow(18);

    @MockitoBean
    private ChapaTuCriptoContract contract;

    @Autowired
    private BlockchainProperties blockchainProperties;

    // ------------------------------------------------------------------ CP019

    @Test
    @DisplayName("CP019 - El saldo es igual a los puntos y las equivalencias son correctas")
    void elSaldoEsIgualALosPuntosConSusEquivalencias() throws Exception {
        var citizen = newCitizen();
        givePoints(citizen, 1250);

        mockMvc.perform(apiGet("/wallet/me").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsBalance").value(1250))
                .andExpect(jsonPath("$.ctcEquivalent").value("1250"))
                .andExpect(jsonPath("$.solesEquivalent").value("12.50"))
                .andExpect(jsonPath("$.network").value("polygon"))
                .andExpect(jsonPath("$.minWithdrawalPoints").value(500))
                .andExpect(jsonPath("$.withdrawalsEnabled").value(true))
                .andExpect(jsonPath("$.hasWithdrawalInProgress").value(false))
                .andExpect(jsonPath("$.linkedWallet").doesNotExist());

        // Tras un retiro COMPLETADO, linkedWallet queda vinculada a esa direccion.
        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getTransactionHash()).thenReturn("0xlink");
        when(contract.mintWithdrawal(eq(VALID_ADDR), any(), eq(BigInteger.valueOf(1250).multiply(CTC_UNIT))))
                .thenReturn(receipt);
        mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", VALID_ADDR, "points", 1250, "mode", "CTC"))))
                .andExpect(status().isOk());

        mockMvc.perform(apiGet("/wallet/me").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsBalance").value(0))
                .andExpect(jsonPath("$.linkedWallet").value(VALID_ADDR));
    }

    // ------------------------------------------------------------------ CP020

    @Test
    @DisplayName("CP020 - Retiro en CTC: 200 COMPLETADO con txHash y explorerUrl de polygonscan")
    void retiroEnCtcCompletaConTxHashYExplorerUrl() throws Exception {
        var citizen = newCitizen();
        givePoints(citizen, 1000);

        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getTransactionHash()).thenReturn("0xctc1000");
        when(contract.mintWithdrawal(eq(VALID_ADDR), any(), eq(BigInteger.valueOf(1000).multiply(CTC_UNIT))))
                .thenReturn(receipt);

        mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", VALID_ADDR, "points", 1000, "mode", "CTC"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("CTC"))
                .andExpect(jsonPath("$.points").value(1000))
                .andExpect(jsonPath("$.status").value("COMPLETADO"))
                .andExpect(jsonPath("$.txHash").value("0xctc1000"))
                .andExpect(jsonPath("$.explorerUrl").value(
                        blockchainProperties.explorerTxUrl("0xctc1000")));
    }

    @Test
    @DisplayName("CP020 - Retiro en USDC: COMPLETADO con reserveOut decodificado del recibo")
    void retiroEnUsdcDecodificaReserveOut() throws Exception {
        var citizen = newCitizen();
        givePoints(citizen, 1000);

        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getTransactionHash()).thenReturn("0xusdc1000");
        when(contract.payoutReserve(eq(VALID_ADDR), any(), eq(BigInteger.valueOf(1000).multiply(CTC_UNIT))))
                .thenReturn(receipt);
        when(contract.decodeReserveOut(receipt)).thenReturn(BigInteger.valueOf(2_777_777L));

        mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", VALID_ADDR, "points", 1000, "mode", "USDC"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("USDC"))
                .andExpect(jsonPath("$.status").value("COMPLETADO"))
                .andExpect(jsonPath("$.reserveOut").value("2777777"));
    }

    @Test
    @DisplayName("CP020 - El historial en /wallet/me/withdrawals lista los retiros del mas reciente al mas antiguo")
    void elHistorialListaLosRetirosDelMasRecienteAlMasAntiguo() throws Exception {
        var citizen = newCitizen();
        givePoints(citizen, 1500);

        TransactionReceipt r1 = mock(TransactionReceipt.class);
        when(r1.getTransactionHash()).thenReturn("0xprimero");
        when(contract.mintWithdrawal(eq(VALID_ADDR), any(), eq(BigInteger.valueOf(500).multiply(CTC_UNIT))))
                .thenReturn(r1);
        mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", VALID_ADDR, "points", 500, "mode", "CTC"))))
                .andExpect(status().isOk());

        TransactionReceipt r2 = mock(TransactionReceipt.class);
        when(r2.getTransactionHash()).thenReturn("0xsegundo");
        when(contract.mintWithdrawal(eq(VALID_ADDR), any(), eq(BigInteger.valueOf(500).multiply(CTC_UNIT))))
                .thenReturn(r2);
        mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", VALID_ADDR, "points", 500, "mode", "CTC"))))
                .andExpect(status().isOk());

        mockMvc.perform(apiGet("/wallet/me/withdrawals").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].txHash").value("0xsegundo"))
                .andExpect(jsonPath("$[1].txHash").value("0xprimero"));
    }

    // ------------------------------------------------------------------ CP021

    @Test
    @DisplayName("CP021 - Direccion con checksum invalido: 400 ERR_BC_004, sin debitar puntos ni tocar el contrato")
    void direccionInvalidaNoDebitaNiTocaElContrato() throws Exception {
        var citizen = newCitizen();
        givePoints(citizen, 1000);

        mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", CHECKSUM_INVALIDO, "points", 500, "mode", "CTC"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_BC_004"));

        mockMvc.perform(apiGet("/wallet/me").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsBalance").value(1000));
        verifyNoInteractions(contract);
    }
}
