package com.sidru.sidru_api.cp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CP-WS-08 y 09 — Seguridad del login social (US-WS-06). */
@DisplayName("CP-WS-08,09 - Seguridad del login social")
class CpWs08Cp09SeguridadSocialTest extends CpWsBase {

    @Test
    @DisplayName("CP-WS-08: una cuenta ROLE_ADMIN nunca se abre ni se vincula por login social (409)")
    void adminNoSeAbrePorLoginSocial() throws Exception {
        var admin = newUser("ROLE_ADMIN");
        long before = socialIdentityRepository.count();

        google(googleToken(uniqueSub(), admin.email(), true, "Intruso"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_SOCIAL_ADMIN"))
                .andExpect(jsonPath("$.token").doesNotExist());
        apple(appleToken(uniqueSub(), admin.email(), true), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_SOCIAL_ADMIN"));

        assertEquals(before, socialIdentityRepository.count(), "no se vincula ninguna identidad");
    }

    @Test
    @DisplayName("CP-WS-08: si la identidad ya estaba vinculada y despues la cuenta pasa a ADMIN, tambien se rechaza")
    void identidadVinculadaDeCuentaQueSeVuelveAdmin() throws Exception {
        var user = newCitizen();
        String sub = uniqueSub();
        google(googleToken(sub, user.email(), true, "Futuro Admin")).andExpect(status().isOk());

        var entity = userRepository.findById(user.id()).orElseThrow();
        entity.addRole(roleRepository.findByName(com.sidru.sidru_api.iam.domain.model.valueobjects.Roles.ROLE_ADMIN)
                .orElseThrow());
        userRepository.save(entity);

        google(googleToken(sub, user.email(), true, "Futuro Admin"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_SOCIAL_ADMIN"));
    }

    @Test
    @DisplayName("CP-WS-09: email_verified=false con una cuenta existente no se vincula (409)")
    void correoNoVerificadoNoVincula() throws Exception {
        var existing = newCitizen();
        long before = socialIdentityRepository.count();

        google(googleToken(uniqueSub(), existing.email(), false, "Sin Verificar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_AUTH_EMAIL_NOT_VERIFIED"))
                .andExpect(jsonPath("$.token").doesNotExist());

        assertEquals(before, socialIdentityRepository.count());
    }

    @Test
    @DisplayName("El correo se compara sin distinguir mayusculas al vincular")
    void vinculacionIgnoraMayusculas() throws Exception {
        var existing = newCitizen();
        google(googleToken(uniqueSub(), existing.email().toUpperCase(), true, "Mayusculas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existing.id()))
                .andExpect(jsonPath("$.newUser").value(false));
    }
}
