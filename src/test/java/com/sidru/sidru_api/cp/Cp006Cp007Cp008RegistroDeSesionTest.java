package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.audit.domain.model.valueobjects.AuditEventType;
import com.sidru.sidru_api.audit.infrastructure.persistence.jpa.repositories.AuditLogRepository;
import com.sidru.sidru_api.sessions.domain.model.valueobjects.SessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP006 / CP007 / CP008 — Registro de sesion de reciclaje enviada por el Smart Bin.
 * HU: US-15 · Escenarios 1, 2 y 3 · Prioridad: Alta
 *
 * <p>DESVIACION DOCUMENTADA respecto de la hoja del CP: el contrato real no lleva
 * {@code deviceId} ni {@code qrHash} en el cuerpo. El dispositivo se identifica con la
 * cabecera {@code X-Device-Api-Key} (una API key por Smart Bin, mas segura que enviar el
 * identificador en el payload) y el {@code qrToken} lo genera el backend al cerrar la
 * sesion, justamente para que el dispositivo no pueda elegirlo. Los pasos se ejecutan
 * contra ese contrato.</p>
 */
@DisplayName("CP006/CP007/CP008 - Registro de sesion de reciclaje")
class Cp006Cp007Cp008RegistroDeSesionTest extends CpBaseTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    // ------------------------------------------------------------------ CP006

    @Test
    @DisplayName("CP006 - Payload valido: 201, cuerpo con qrToken y expiresAt, sesion PENDING persistida")
    void registraLaSesionConPayloadValido() throws Exception {
        var bin = newSmartBin();

        // Paso 1 — POST /api/v1/sessions con un payload valido -> HTTP 201.
        String body = mockMvc.perform(apiPost("/sessions")
                        .header("X-Device-Api-Key", bin.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("capCount", 25, "weightGrams", 57.4))))
                .andExpect(status().isCreated())
                // Paso 2 — El cuerpo contiene los campos qrToken y expiresAt.
                .andExpect(jsonPath("$.qrToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        // Paso 3 — La sesion persiste con estado PENDING y los datos enviados.
        String qrToken = field(body, "qrToken", String.class);
        var persisted = sessionRepository.findByQrToken(qrToken).orElseThrow();

        assertEquals(SessionStatus.PENDING, persisted.getStatus());
        assertEquals(25, persisted.getCapCount());
        assertEquals(57.4, persisted.getWeightGrams(), 0.0001);
        assertEquals(bin.getId(), persisted.getSmartBinId());
        assertNotNull(persisted.getExpiresAt());
        assertTrue(persisted.getExpiresAt().isAfter(persisted.getCreatedAt()),
                "la vigencia del QR debe ser posterior al momento de registro");
    }

    // ------------------------------------------------------------------ CP007

    @Test
    @DisplayName("CP007 - weightGrams = 0: 400 con campo y motivo, y sin registro creado")
    void rechazaPesoFueraDeRangoSinPersistir() throws Exception {
        var bin = newSmartBin();
        long before = sessionRepository.count();

        // Paso 1 — POST con weightGrams igual a cero -> HTTP 400.
        // Paso 2 — El cuerpo contiene el campo rechazado y el motivo del rechazo.
        mockMvc.perform(apiPost("/sessions")
                        .header("X-Device-Api-Key", bin.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("capCount", 25, "weightGrams", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_SES_006"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details[0]",
                        org.hamcrest.Matchers.startsWith("weightGrams:")));

        // Paso 3 — No se creo ningun registro.
        assertEquals(before, sessionRepository.count(), "la base de datos debe quedar sin cambios");
    }

    @Test
    @DisplayName("CP007 - Falta un campo obligatorio: 400 con el mismo formato de error")
    void rechazaPayloadSinCampoObligatorio() throws Exception {
        var bin = newSmartBin();
        long before = sessionRepository.count();

        // Paso 4 — Repetir la peticion omitiendo un campo obligatorio (capCount).
        var payloadSinCapCount = new HashMap<String, Object>();
        payloadSinCapCount.put("weightGrams", 57.4);

        mockMvc.perform(apiPost("/sessions")
                        .header("X-Device-Api-Key", bin.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payloadSinCapCount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_SES_007"))
                .andExpect(jsonPath("$.details[0]",
                        org.hamcrest.Matchers.startsWith("capCount:")));

        assertEquals(before, sessionRepository.count());
    }

    @Test
    @DisplayName("CP007 - capCount fuera de rango: 400 indicando el campo rechazado")
    void rechazaCapCountFueraDeRango() throws Exception {
        var bin = newSmartBin();

        // El maximo configurado es 500 tapas por sesion.
        mockMvc.perform(apiPost("/sessions")
                        .header("X-Device-Api-Key", bin.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("capCount", 5000, "weightGrams", 120.0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_SES_004"))
                .andExpect(jsonPath("$.details[0]",
                        org.hamcrest.Matchers.startsWith("capCount:")));
    }

    // ------------------------------------------------------------------ CP008

    @Test
    @DisplayName("CP008 - Dispositivo no registrado: 403, intento auditado y sin sesion creada")
    void rechazaDispositivoNoRegistradoYAuditaElIntento() throws Exception {
        long sessionsBefore = sessionRepository.count();
        long auditBefore = auditLogRepository.countByEventType(AuditEventType.DEVICE_AUTH_FAILURE);
        String unknownApiKey = "SIDRU-SB-999-NO-REGISTRADA";

        // Paso 1 — POST con un deviceId (API key) no registrado -> HTTP 403.
        mockMvc.perform(apiPost("/sessions")
                        .header("X-Device-Api-Key", unknownApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("capCount", 10, "weightGrams", 30.0))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_SES_005"));

        // Paso 2 — El intento queda registrado con fecha, deviceId y origen de la peticion.
        long auditAfter = auditLogRepository.countByEventType(AuditEventType.DEVICE_AUTH_FAILURE);
        assertEquals(auditBefore + 1, auditAfter, "el intento debe quedar asentado en audit_logs");

        var asiento = auditLogRepository.findByEventTypeOrderByIdDesc(AuditEventType.DEVICE_AUTH_FAILURE)
                .get(0);
        assertNotNull(asiento.getCreatedAt(), "el asiento debe llevar fecha");
        assertNotNull(asiento.getOrigin(), "el asiento debe llevar el origen de la peticion");
        assertTrue(asiento.getDetail().contains("/sessions"),
                "el asiento debe indicar el recurso solicitado, fue: " + asiento.getDetail());

        // La credencial presentada nunca se guarda en claro: solo su huella.
        assertTrue(asiento.getSubject().startsWith("apikey:"),
                "el sujeto debe ser una huella, fue: " + asiento.getSubject());
        assertTrue(!asiento.getSubject().contains(unknownApiKey),
                "la API key presentada no debe quedar en claro en el log de auditoria");

        // Paso 3 — No se creo ningun registro de sesion.
        assertEquals(sessionsBefore, sessionRepository.count());
    }
}
