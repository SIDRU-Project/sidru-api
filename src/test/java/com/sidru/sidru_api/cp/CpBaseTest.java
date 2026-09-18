package com.sidru.sidru_api.cp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sidru.sidru_api.devices.domain.model.aggregates.SmartBin;
import com.sidru.sidru_api.devices.infrastructure.persistence.jpa.repositories.SmartBinRepository;
import com.sidru.sidru_api.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import com.sidru.sidru_api.sessions.domain.model.aggregates.RecyclingSession;
import com.sidru.sidru_api.sessions.infrastructure.persistence.jpa.repositories.RecyclingSessionRepository;
import com.sidru.sidru_api.users.infrastructure.persistence.jpa.repositories.UserProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de los casos de prueba automatizados del plan de pruebas (TP202610003_CP.xlsx).
 *
 * <p>Levanta el contexto completo de Spring sobre el perfil {@code test} (H2 en memoria,
 * MQTT/Firebase/blockchain apagados) y ejerce la API por HTTP con MockMvc, de modo que
 * cada paso del caso de prueba se ejecuta contra el mismo camino que recorre un cliente
 * real: filtro de rate limit, filtro JWT, autorizacion, controller, servicio y base de
 * datos.</p>
 *
 * <p>Las rutas se escriben completas ({@code /api/v1/...}) para que coincidan literalmente
 * con lo que dice la hoja del caso de prueba; {@link #api} fija el context-path para que
 * MockMvc resuelva igual que el servlet real.</p>
 *
 * <p>Cada test crea sus propios datos con identificadores unicos (correo, deviceCode), asi
 * que el orden de ejecucion es irrelevante y no hay estado compartido entre casos.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class CpBaseTest {

    protected static final String API = "/api/v1";

    /** Contrasena valida segun la politica de registro (mayuscula, minuscula y digito). */
    protected static final String PASSWORD = "Sidru2026a";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected UserProfileRepository userProfileRepository;
    @Autowired protected SmartBinRepository smartBinRepository;
    @Autowired protected RecyclingSessionRepository sessionRepository;

    // ------------------------------------------------------------------ HTTP

    /** Constructor de peticiones con el context-path real de la API ({@code /api/v1}). */
    protected MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.contextPath(API);
    }

    protected MockHttpServletRequestBuilder apiGet(String path) {
        return api(MockMvcRequestBuilders.get(API + path));
    }

    protected MockHttpServletRequestBuilder apiPost(String path) {
        return api(MockMvcRequestBuilders.post(API + path));
    }

    protected MockHttpServletRequestBuilder apiPut(String path) {
        return api(MockMvcRequestBuilders.put(API + path));
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /** Lee un campo de primer nivel de una respuesta JSON. */
    protected <T> T field(String responseBody, String name, Class<T> type) throws Exception {
        Map<?, ?> map = objectMapper.readValue(responseBody, Map.class);
        return type.cast(map.get(name));
    }

    // ------------------------------------------------------- Usuarios y token

    /**
     * Ciudadano de prueba ya registrado: identificador, correo y JWT vigente.
     */
    protected record TestUser(Long id, String email, String token) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    /** Registra un ciudadano nuevo (correo unico) y devuelve su sesion autenticada. */
    protected TestUser newCitizen() throws Exception {
        return newUser(null);
    }

    /** Registra un usuario nuevo con el rol indicado ({@code null} = ciudadano por defecto). */
    protected TestUser newUser(String role) throws Exception {
        String email = "cp-" + UUID.randomUUID().toString().substring(0, 12) + "@upc.edu.pe";

        var signUp = new java.util.HashMap<String, Object>();
        signUp.put("fullName", "Usuario Piloto");
        signUp.put("email", email);
        signUp.put("password", PASSWORD);
        signUp.put("district", "San Miguel");
        if (role != null) signUp.put("roles", java.util.List.of(role));

        mockMvc.perform(apiPost("/authentication/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(signUp)))
                .andExpect(status().isCreated());

        return new TestUser(userRepository.findByEmail(email).orElseThrow().getId(), email, signIn(email));
    }

    /** Autentica y devuelve el JWT emitido. */
    protected String signIn(String email) throws Exception {
        String body = mockMvc.perform(apiPost("/authentication/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return field(body, "token", String.class);
    }

    // --------------------------------------------------- Dispositivos y sesiones

    /** Registra un Smart Bin nuevo directamente en persistencia y devuelve su API key. */
    protected SmartBin newSmartBin() {
        String code = "CP-BIN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return smartBinRepository.save(new SmartBin(code, "Av. Universitaria 1800", "San Miguel"));
    }

    /**
     * Abre una sesion PENDING por el mismo camino que el Smart Bin real
     * (POST /sessions con X-Device-Api-Key) y devuelve la sesion persistida.
     */
    protected RecyclingSession openSession(SmartBin bin, int capCount, double weightGrams) throws Exception {
        String body = mockMvc.perform(apiPost("/sessions")
                        .header("X-Device-Api-Key", bin.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("capCount", capCount, "weightGrams", weightGrams))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String qrToken = field(body, "qrToken", String.class);
        return sessionRepository.findByQrToken(qrToken).orElseThrow();
    }

    /**
     * Acredita exactamente {@code points} puntos al ciudadano confirmando una sesion nueva
     * (price-per-kg-soles=4.00, points-per-sol=100 -> 400 puntos/kg, ver application-test.properties).
     * Util para las pruebas de retiro (spec sidru-mainnet), que parten de un saldo de puntos dado.
     */
    protected void givePoints(TestUser citizen, int points) throws Exception {
        var bin = newSmartBin();
        double weightGrams = points * 2.5; // 1000 g/kg / 400 puntos/kg
        var session = openSession(bin, 20, weightGrams);
        mockMvc.perform(apiPost("/sessions/qr/" + session.getQrToken() + "/confirm")
                        .header("Authorization", citizen.bearer()))
                .andExpect(status().isOk());
    }
}
