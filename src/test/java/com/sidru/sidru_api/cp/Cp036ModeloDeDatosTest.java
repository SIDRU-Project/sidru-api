package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.rewards.domain.model.aggregates.Reward;
import com.sidru.sidru_api.rewards.infrastructure.persistence.jpa.repositories.RewardRepository;
import com.sidru.sidru_api.sessions.domain.model.aggregates.RecyclingSession;
import com.sidru.sidru_api.users.domain.model.aggregates.UserProfile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP036 — Migraciones y relaciones del modelo de datos.
 * HU: US-02 · Escenario 1 · Prioridad: Alta
 * Criterio de aceptacion: Implementacion correcta de las entidades.
 *
 * <p>DESVIACION DOCUMENTADA: el esquema no se versiona con migraciones explicitas (Flyway o
 * Liquibase) sino que lo deriva Hibernate del modelo de entidades ({@code ddl-auto=update} en
 * despliegue, {@code create-drop} en pruebas). El "paso 1" equivale entonces a que el esquema
 * se genere sin errores al arrancar, que es lo que verifica esta clase junto con la existencia
 * de tablas, las restricciones y las operaciones de alta y consulta por entidad.</p>
 */
@DisplayName("CP036 - Migraciones y relaciones del modelo de datos")
class Cp036ModeloDeDatosTest extends CpBaseTest {

    /**
     * Tablas que exige el modelo de datos definido.
     *
     * <p>NOTA (spec sidru-mainnet, Fase 3): {@code blockchain_transactions} y
     * {@code user_wallet_addresses} salieron del modelo junto con {@code BlockchainTransaction}
     * / {@code UserWalletAddress} (tarea 3.0): el mint por sesion y la direccion custodial por
     * usuario dejaron de existir; el unico artefacto on-chain es el retiro.</p>
     */
    private static final List<String> EXPECTED_TABLES = List.of(
            "users", "roles", "user_profiles", "smart_bins", "recycling_sessions",
            "rewards", "point_transactions",
            "withdrawal_requests", "device_logs", "audit_logs");

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RewardRepository rewardRepository;

    @Test
    @DisplayName("Paso 1-2: el esquema se genera y existen las tablas de usuarios, sesiones, recompensas y registros")
    void elEsquemaSeGeneraConTodasLasTablasDelModelo() {
        Set<String> actual = jdbcTemplate
                .queryForList("select table_name from information_schema.tables "
                        + "where table_schema = 'PUBLIC'", String.class)
                .stream().map(String::toLowerCase).collect(Collectors.toSet());

        for (String table : EXPECTED_TABLES) {
            assertTrue(actual.contains(table),
                    "falta la tabla '" + table + "' en el esquema generado. Tablas presentes: " + actual);
        }
    }

    @Test
    @DisplayName("Paso 3: las restricciones del modelo se aplican en el esquema")
    void lasRestriccionesDelModeloSeAplicanEnElEsquema() {
        // Unicidad del correo (credencial) y del token del QR (anti reutilizacion).
        assertTrue(hasUniqueConstraint("users", "email"),
                "el correo de usuario debe ser unico");
        assertTrue(hasUniqueConstraint("recycling_sessions", "qr_token"),
                "el token del QR debe ser unico");
        assertTrue(hasUniqueConstraint("smart_bins", "api_key"),
                "la API key del Smart Bin debe ser unica");
        assertTrue(hasUniqueConstraint("user_profiles", "user_id"),
                "cada usuario debe tener un unico perfil");

        // Relacion usuario-rol: tabla de union generada por la asociacion @ManyToMany.
        Integer joinTables = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_schema = 'PUBLIC' and lower(table_name) in ('user_roles', 'users_roles')",
                Integer.class);
        assertTrue(joinTables != null && joinTables > 0,
                "debe existir la tabla de union entre usuarios y roles");

        // Columnas no nulas exigidas por el modelo.
        assertTrue(isNotNullable("recycling_sessions", "qr_token"));
        assertTrue(isNotNullable("recycling_sessions", "smart_bin_id"));
        assertTrue(isNotNullable("recycling_sessions", "status"));
    }

    @Test
    @DisplayName("Paso 4: alta y consulta por entidad se completan correctamente mediante JPA")
    void cadaEntidadAdmiteAltaYConsultaPorJpa() throws Exception {
        // SmartBin
        var bin = newSmartBin();
        assertNotNull(smartBinRepository.findById(bin.getId()).orElseThrow().getApiKey());

        // User + UserProfile (alta por el flujo real de registro)
        var citizen = newCitizen();
        UserProfile profile = userProfileRepository.findByUserId(citizen.id()).orElseThrow();
        assertEquals(citizen.id(), profile.getUserId());
        assertNotNull(userRepository.findById(citizen.id()).orElseThrow().getEmail());

        // RecyclingSession
        var session = sessionRepository.save(new RecyclingSession(
                bin.getId(), 12, 40.0, 16, LocalDateTime.now().plusMinutes(15)));
        assertNotNull(sessionRepository.findById(session.getId()).orElseThrow().getQrToken());

        // Reward
        Reward reward = rewardRepository.save(
                new Reward("Cupon CP036", "Recompensa de prueba", 100, 5, null));
        assertEquals("Cupon CP036", rewardRepository.findById(reward.getId()).orElseThrow().getName());

        // Las marcas de auditoria (createdAt/updatedAt) se rellenan solas en todas las entidades.
        assertNotNull(session.getCreatedAt());
        assertNotNull(session.getUpdatedAt());
        assertNotNull(profile.getCreatedAt());

        // Y el EntityManager resuelve las entidades registradas en el metamodelo.
        Set<String> managed = entityManager.getMetamodel().getEntities().stream()
                .map(e -> e.getName().toLowerCase())
                .collect(Collectors.toSet());
        for (String entity : List.of("user", "userprofile", "smartbin", "recyclingsession",
                "reward", "auditlog")) {
            assertTrue(managed.contains(entity),
                    "la entidad '" + entity + "' debe estar mapeada. Mapeadas: " + managed);
        }
    }

    private boolean hasUniqueConstraint(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.key_column_usage k "
                        + "join information_schema.table_constraints c "
                        + "  on k.constraint_name = c.constraint_name "
                        + "where lower(k.table_name) = ? and lower(k.column_name) = ? "
                        + "  and c.constraint_type in ('UNIQUE', 'PRIMARY KEY')",
                Integer.class, table, column);
        return count != null && count > 0;
    }

    private boolean isNotNullable(String table, String column) {
        String nullable = jdbcTemplate.queryForObject(
                "select is_nullable from information_schema.columns "
                        + "where lower(table_name) = ? and lower(column_name) = ?",
                String.class, table, column);
        return "NO".equalsIgnoreCase(nullable);
    }
}
