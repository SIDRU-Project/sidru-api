package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.blockchain.infrastructure.persistence.jpa.repositories.WithdrawalRequestRepository;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.ChapaTuCriptoContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP019 — Coincidencia del saldo mostrado con el balance on-chain (US-25, esc. 3).
 * CP020 — Retiro de tokens a una wallet externa (US-38, esc. 1).
 * CP021 — Rechazo de retiro con direccion de checksum invalido (US-38, esc. 2).
 *
 * <p>La integracion blockchain se activa por propiedad y el contrato se sustituye por un
 * doble: lo que verifica el CP es que el backend exponga exactamente lo que dice la cadena
 * y que no ejecute transacciones cuando la direccion de destino no es valida.</p>
 *
 * <p>DESVIACION DOCUMENTADA (CP020): el retiro es siempre del saldo completo, por decision
 * de arquitectura (modelo custodial hibrido); no se envia un monto en la peticion. El caso
 * se ejerce con un saldo de exactamente 50 CTC para equivaler al dato de prueba de la hoja.
 * El paso 4 (ver la wallet externa en el explorador) es verificacion manual on-chain.</p>
 */
@TestPropertySource(properties = "sidru.blockchain.enabled=true")
@DisplayName("CP019/CP020/CP021 - Wallet custodial: saldo y retiro")
class Cp019Cp020Cp021WalletCustodialTest extends CpBaseTest {

    private static final BigInteger WEI_PER_CTC = BigInteger.TEN.pow(18);
    private static final BigInteger CINCUENTA_CTC = BigInteger.valueOf(50).multiply(WEI_PER_CTC);
    private static final String DIRECCION_VALIDA = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";
    /** Misma direccion con un caracter cambiado de caja: formato correcto, checksum EIP-55 invalido. */
    private static final String DIRECCION_CHECKSUM_INVALIDO = "0x5aAeb6053f3E94C9b9A09f33669435E7Ef1BeAed";
    private static final String TX_HASH = "0x9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b";

    @MockitoBean
    private ChapaTuCriptoContract contract;

    @Autowired
    private WithdrawalRequestRepository withdrawalRepository;

    // ------------------------------------------------------------------ CP019

    @Test
    @DisplayName("CP019 - Paso 1-3: el saldo expuesto coincide exactamente con balanceOf del contrato")
    void elSaldoExpuestoCoincideConElBalanceOnChain() throws Exception {
        var citizen = newCitizen();
        when(contract.balanceOf(anyString())).thenReturn(CINCUENTA_CTC);

        // Paso 1 — La pantalla Wallet muestra el saldo de CTC y su equivalente en soles.
        String body = mockMvc.perform(apiGet("/wallet/me").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").isNotEmpty())
                .andExpect(jsonPath("$.network").value("polygon-amoy"))
                .andExpect(jsonPath("$.solesRef").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        // Paso 2 — Consultar balanceOf de la direccion custodial directamente en el contrato.
        String custodialAddress = field(body, "address", String.class);
        BigInteger onChain = contract.balanceOf(custodialAddress);

        // Paso 3 — Ambos valores coinciden exactamente, con tolerancia cero.
        assertEquals(onChain.toString(), field(body, "balanceWei", String.class),
                "el saldo en wei expuesto debe ser identico al balance on-chain");
        assertEquals(new BigDecimal(onChain).divide(new BigDecimal(WEI_PER_CTC)).toPlainString(),
                field(body, "balanceCtc", String.class),
                "el saldo en CTC debe ser el balance on-chain convertido con 18 decimales");
        assertEquals("0.50", field(body, "solesRef", String.class),
                "la equivalencia referencial en soles usa dos decimales (100 CTC = S/ 1.00)");
    }

    @Test
    @DisplayName("CP019 - Un saldo distinto en la cadena se refleja tal cual, sin cache ni redondeo")
    void reflejaCualquierCambioDelBalanceOnChain() throws Exception {
        var citizen = newCitizen();
        BigInteger balanceIrregular = new BigInteger("1234567890123456789"); // 1,234... CTC
        when(contract.balanceOf(anyString())).thenReturn(balanceIrregular);

        mockMvc.perform(apiGet("/wallet/me").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceWei").value(balanceIrregular.toString()));
    }

    // ------------------------------------------------------------------ CP020

    @Test
    @DisplayName("CP020 - Paso 1-3: retiro a wallet externa valida devuelve COMPLETADO con txHash")
    void retiraElSaldoHaciaUnaWalletExternaValida() throws Exception {
        var citizen = newCitizen();

        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getTransactionHash()).thenReturn(TX_HASH);
        // Saldo antes del retiro y saldo despues (ya transferido).
        when(contract.balanceOf(anyString()))
                .thenReturn(CINCUENTA_CTC)
                .thenReturn(BigInteger.ZERO);
        when(contract.withdrawTo(anyString(), eq(DIRECCION_VALIDA), eq(CINCUENTA_CTC)))
                .thenReturn(receipt);

        // Paso 1 — POST /api/v1/wallet/withdraw con la direccion de destino -> HTTP 200.
        // Paso 2 — El cuerpo contiene status COMPLETADO y el txHash de la transferencia.
        mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", DIRECCION_VALIDA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETADO"))
                .andExpect(jsonPath("$.txHash").value(TX_HASH))
                .andExpect(jsonPath("$.toAddress").value(DIRECCION_VALIDA))
                .andExpect(jsonPath("$.amountWei").value(CINCUENTA_CTC.toString()))
                .andExpect(jsonPath("$.explorerUrl",
                        org.hamcrest.Matchers.containsString("amoy.polygonscan.com")));

        // El backend transfiere desde la custodia hacia el destino: el ciudadano no firma ni paga gas.
        verify(contract).withdrawTo(anyString(), eq(DIRECCION_VALIDA), eq(CINCUENTA_CTC));

        // Paso 3 — El saldo del usuario refleja el descuento del monto retirado.
        mockMvc.perform(apiGet("/wallet/me").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceWei").value("0"))
                .andExpect(jsonPath("$.linkedWallet").value(DIRECCION_VALIDA));

        // Paso 4 (equivalente automatizable) — el retiro queda en el historial on-chain del usuario.
        mockMvc.perform(apiGet("/wallet/me/transactions").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'WITHDRAW')].txHash",
                        org.hamcrest.Matchers.hasItem(TX_HASH)));
    }

    // ------------------------------------------------------------------ CP021

    @Test
    @DisplayName("CP021 - Paso 1-3: direccion con checksum invalido devuelve 400 y no ejecuta transaccion")
    void rechazaLaDireccionConChecksumInvalidoSinEjecutarTransaccion() throws Exception {
        var citizen = newCitizen();
        when(contract.balanceOf(anyString())).thenReturn(CINCUENTA_CTC);
        long retirosAntes = withdrawalRepository.count();

        // Paso 1 — POST /api/v1/wallet/withdraw con una direccion de checksum invalido -> HTTP 400.
        // Paso 2 — El mensaje indica que la direccion de destino es invalida.
        mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", DIRECCION_CHECKSUM_INVALIDO))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_BC_004"))
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("direcci")));

        // Paso 3 — El saldo no varia y no se ejecuto ninguna transaccion.
        verify(contract, never()).withdrawTo(anyString(), anyString(), any());
        assertEquals(retirosAntes, withdrawalRepository.count(),
                "una direccion invalida no debe generar ninguna solicitud de retiro");

        mockMvc.perform(apiGet("/wallet/me").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceWei").value(CINCUENTA_CTC.toString()));
    }

    @Test
    @DisplayName("CP021 - Direcciones malformadas (longitud, caracteres no hex) tambien se rechazan")
    void rechazaDireccionesMalformadas() throws Exception {
        var citizen = newCitizen();

        for (String invalida : java.util.List.of(
                "0x123",                                            // longitud incorrecta
                "5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed",         // sin prefijo 0x
                "0xZZAeb6053F3E94C9b9A09f33669435E7Ef1BeAed")) {    // caracteres no hexadecimales
            mockMvc.perform(apiPost("/wallet/withdraw")
                            .header("Authorization", citizen.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("toAddress", invalida))))
                    .andExpect(status().isBadRequest());
        }

        verify(contract, never()).withdrawTo(anyString(), anyString(), any());
    }
}
