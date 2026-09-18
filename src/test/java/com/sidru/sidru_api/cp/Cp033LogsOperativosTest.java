package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.devices.interfaces.acl.DevicesContextFacade;
import com.sidru.sidru_api.iam.domain.model.valueobjects.Roles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP033 — Envio de logs operativos del Smart Bin al backend.
 * HU: US-30 · Escenario 1 · Prioridad: Baja
 * Criterio de aceptacion: Envio de registros operativos.
 *
 * <p>DESVIACION DOCUMENTADA: el dispositivo publica sus registros por MQTT, no por HTTP. En
 * este caso automatizado se entra por el puerto que usa el listener MQTT
 * ({@code DevicesContextFacade.recordDeviceLog}), de modo que se verifica todo lo que el CP
 * pide del lado del backend — persistencia con marca de tiempo, identificador de dispositivo
 * y nivel, y consulta filtrable — sin depender de un broker levantado. La publicacion real
 * desde el firmware es el paso manual del protocolo.</p>
 */
@DisplayName("CP033 - Envio de logs operativos del Smart Bin")
class Cp033LogsOperativosTest extends CpBaseTest {

    @Autowired
    private DevicesContextFacade devicesContextFacade;

    @Test
    @DisplayName("Paso 1-4: los registros llegan con marca de tiempo, dispositivo y nivel, y son filtrables")
    void registraYExponeLosLogsOperativosDelDispositivo() throws Exception {
        var admin = newUser(Roles.ROLE_ADMIN.name());
        var bin = newSmartBin();
        String deviceCode = bin.getDeviceCode();

        // Paso 1 — Eventos operativos normales del dispositivo.
        devicesContextFacade.recordDeviceLog(deviceCode, "INFO", "Sesion iniciada", "{\"caps\":0}");
        devicesContextFacade.recordDeviceLog(deviceCode, "INFO", "Tapa detectada", "{\"caps\":1}");

        // Paso 2 — Evento de error controlado, con su nivel de severidad.
        devicesContextFacade.recordDeviceLog(deviceCode, "ERROR", "Celda de carga sin respuesta",
                "{\"sensor\":\"HX711\"}");

        // Paso 3 — Los registros llegaron con su marca de tiempo, identificador y nivel.
        mockMvc.perform(apiGet("/device-logs?deviceCode=" + deviceCode)
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].deviceCode").value(deviceCode))
                .andExpect(jsonPath("$[0].receivedAt").isNotEmpty())
                .andExpect(jsonPath("$[*].type", org.hamcrest.Matchers.hasItem("ERROR")))
                .andExpect(jsonPath("$[*].detail",
                        org.hamcrest.Matchers.hasItem("Celda de carga sin respuesta")));

        // Paso 4 — Los eventos son consultables y filtrables desde la consola de monitoreo.
        var otroBin = newSmartBin();
        devicesContextFacade.recordDeviceLog(otroBin.getDeviceCode(), "INFO", "Otro dispositivo", "{}");

        mockMvc.perform(apiGet("/device-logs?deviceCode=" + otroBin.getDeviceCode())
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].deviceCode").value(otroBin.getDeviceCode()));

        // Sin filtro se ven los ultimos registros de todos los dispositivos.
        mockMvc.perform(apiGet("/device-logs").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()",
                        org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
    }

    @Test
    @DisplayName("La consola de logs es solo para administradores")
    void laConsolaDeLogsEsSoloParaAdministradores() throws Exception {
        var citizen = newCitizen();

        mockMvc.perform(apiGet("/device-logs").header("Authorization", citizen.bearer()))
                .andExpect(status().isForbidden());

        mockMvc.perform(apiGet("/device-logs"))
                .andExpect(status().isUnauthorized());
    }
}
