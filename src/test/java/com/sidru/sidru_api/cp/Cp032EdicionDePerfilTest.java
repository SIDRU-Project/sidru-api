package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.blockchain.domain.model.valueobjects.EvmAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP032 — Edicion del perfil de usuario.
 * HU: US-29 · Escenario 1 · Prioridad: Media
 * Criterio de aceptacion: Actualizacion segura de los datos del perfil.
 *
 * <p>DESVIACION DOCUMENTADA: la hoja habla de modificar "nombre y correo electronico". En el
 * diseno vigente el correo es la credencial de acceso y vive en el contexto {@code iam}: no se
 * edita desde el perfil (cambiarlo invalidaria el JWT y el inicio de sesion). El perfil
 * ({@code users}) edita nombre, telefono y distrito. La validacion de formato de correo se
 * verifica donde el correo si se captura: el registro.</p>
 *
 * <p>La direccion de wallet externa no se guarda en el perfil sino que se vincula al ejecutar
 * el retiro (contexto {@code blockchain}, modelo custodial hibrido); aqui se verifica que el
 * formato EIP-55 valido es aceptado por la validacion y el invalido rechazado.</p>
 */
@DisplayName("CP032 - Edicion del perfil de usuario")
class Cp032EdicionDePerfilTest extends CpBaseTest {

    @Test
    @DisplayName("Paso 1-2: los cambios del perfil se aceptan, se persisten y se reflejan de inmediato")
    void actualizaYPersisteLosDatosDelPerfil() throws Exception {
        var citizen = newCitizen();

        // Paso 1 — Modificar los datos del perfil: la aplicacion acepta los valores y envia la actualizacion.
        mockMvc.perform(apiPut("/profiles/me")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Adriano Cruz Palomino",
                                "phone", "987654321",
                                "district", "Miraflores"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Adriano Cruz Palomino"))
                .andExpect(jsonPath("$.district").value("Miraflores"));

        // Paso 2 — Los cambios se persisten y se reflejan al recargar la pantalla.
        mockMvc.perform(apiGet("/profiles/me").header("Authorization", citizen.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Adriano Cruz Palomino"))
                .andExpect(jsonPath("$.phone").value("987654321"))
                .andExpect(jsonPath("$.district").value("Miraflores"));

        var persisted = userProfileRepository.findByUserId(citizen.id()).orElseThrow();
        assertEquals("Adriano Cruz Palomino", persisted.getFullName());
        assertEquals("Miraflores", persisted.getDistrict());
    }

    @Test
    @DisplayName("Paso 3: un valor que no cumple la validacion se rechaza y no se persiste")
    void rechazaLosValoresQueNoCumplenLaValidacion() throws Exception {
        var citizen = newCitizen();
        String nombreOriginal = userProfileRepository.findByUserId(citizen.id())
                .orElseThrow().getFullName();

        // Telefono por encima del maximo permitido (20 caracteres).
        mockMvc.perform(apiPut("/profiles/me")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Nombre Valido",
                                "phone", "9".repeat(40)))))
                .andExpect(status().isBadRequest());

        assertEquals(nombreOriginal,
                userProfileRepository.findByUserId(citizen.id()).orElseThrow().getFullName(),
                "un payload rechazado no debe aplicar ningun cambio parcial");
    }

    @Test
    @DisplayName("Paso 3 (correo): un correo con formato invalido se rechaza donde se captura el correo")
    void rechazaElCorreoConFormatoInvalido() throws Exception {
        mockMvc.perform(apiPost("/authentication/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Usuario Piloto",
                                "email", "esto-no-es-un-correo",
                                "password", PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.startsWith("email:"))));

        assertTrue(userRepository.findByEmail("esto-no-es-un-correo").isEmpty(),
                "un correo invalido no debe crear usuario");
    }

    @Test
    @DisplayName("Paso 4: una direccion de wallet EIP-55 valida se acepta y una invalida se rechaza")
    void validaLaDireccionDeWalletExterna() throws Exception {
        var citizen = newCitizen();
        String valida = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";
        String invalida = "0x5aAeb6053f3E94C9b9A09f33669435E7Ef1BeAed";

        assertTrue(EvmAddress.isValid(valida), "la direccion con checksum EIP-55 correcto es valida");
        assertFalse(EvmAddress.isValid(invalida), "la direccion con checksum alterado es invalida");

        // En el endpoint: la invalida se rechaza por formato (400); la valida pasa la
        // validacion y el flujo avanza hasta la comprobacion de puntos (422, sin saldo).
        mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", invalida, "points", 500, "mode", "CTC"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_BC_004"));

        mockMvc.perform(apiPost("/wallet/withdraw")
                        .header("Authorization", citizen.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toAddress", valida, "points", 500, "mode", "CTC"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ERR_BC_005"));
    }

    @Test
    @DisplayName("El perfil solo es editable por su dueno: sin token no se puede modificar")
    void elPerfilNoEsEditableSinAutenticacion() throws Exception {
        mockMvc.perform(apiPut("/profiles/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Intruso"))))
                .andExpect(status().isUnauthorized());
    }
}
