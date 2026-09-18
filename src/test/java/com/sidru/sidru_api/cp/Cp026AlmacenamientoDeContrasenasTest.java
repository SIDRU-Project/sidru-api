package com.sidru.sidru_api.cp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP026 — Almacenamiento de contrasenas con BCrypt.
 * HU: US-28 · Escenario 2 · Prioridad: Alta
 * Criterio de aceptacion: Almacenamiento seguro de contrasenas.
 *
 * Postcondicion: no se modifica ningun registro de la base de datos.
 */
@DisplayName("CP026 - Almacenamiento de contrasenas con BCrypt")
class Cp026AlmacenamientoDeContrasenasTest extends CpBaseTest {

    /** Formato BCrypt: $2a$/$2b$/$2y$ + coste de 2 digitos + $ + 53 caracteres de sal y hash. */
    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Paso 1-3: los hashes tienen formato BCrypt y la contrasena en claro no aparece")
    void lasContrasenasSeGuardanHasheadasYNuncaEnClaro() throws Exception {
        // Precondicion: base de datos poblada con al menos cinco usuarios registrados.
        List<String> emails = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            emails.add(newCitizen().email());
        }

        // Paso 1 — Consultar la columna de contrasena de la tabla users.
        List<String> hashes = jdbcTemplate.queryForList(
                "select password from users where email in (?, ?, ?, ?, ?)",
                String.class,
                emails.get(0), emails.get(1), emails.get(2), emails.get(3), emails.get(4));

        assertEquals(5, hashes.size(), "deben recuperarse los cinco usuarios sembrados");

        // Paso 2 — Inspeccionar el formato de cada valor: prefijo caracteristico de BCrypt.
        for (String hash : hashes) {
            assertTrue(BCRYPT.matcher(hash).matches(),
                    "el valor almacenado debe ser un hash BCrypt, fue: " + hash);
            assertFalse(hash.equals(PASSWORD), "la contrasena no debe almacenarse en claro");
        }

        // Cada hash es distinto aunque la contrasena sea la misma: BCrypt aplica sal por usuario.
        assertEquals(5, hashes.stream().distinct().count(),
                "misma contrasena y hashes distintos: la sal debe ser unica por usuario");

        // Paso 3 — Busqueda de contrasenas en texto plano sobre la base de datos: sin coincidencias.
        Integer coincidencias = jdbcTemplate.queryForObject(
                "select count(*) from users where password = ? or password like ?",
                Integer.class, PASSWORD, "%" + PASSWORD + "%");
        assertEquals(0, coincidencias, "ninguna fila debe contener la contrasena en texto plano");
    }

    @Test
    @DisplayName("La respuesta de la API nunca expone el hash ni la contrasena")
    void laApiNoExponeLaContrasenaEnNingunaRespuesta() throws Exception {
        var citizen = newCitizen();

        String perfil = mockMvc.perform(apiGet("/profiles/me").header("Authorization", citizen.bearer()))
                .andReturn().getResponse().getContentAsString();
        String usuario = mockMvc.perform(apiGet("/users/" + citizen.id())
                        .header("Authorization", citizen.bearer()))
                .andReturn().getResponse().getContentAsString();

        for (String body : List.of(perfil, usuario)) {
            assertFalse(body.contains(PASSWORD), "la respuesta no debe traer la contrasena en claro");
            assertFalse(body.contains("$2a$") || body.contains("$2b$"),
                    "la respuesta no debe traer el hash de la contrasena");
        }
    }
}
