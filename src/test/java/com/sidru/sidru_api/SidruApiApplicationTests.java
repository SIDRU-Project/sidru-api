package com.sidru.sidru_api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Arranque del contexto completo (CP035, escenario 1). Corre sobre el perfil "test"
 * (H2 en memoria, integraciones externas apagadas), asi que no necesita PostgreSQL.
 */
@SpringBootTest
@ActiveProfiles("test")
class SidruApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
