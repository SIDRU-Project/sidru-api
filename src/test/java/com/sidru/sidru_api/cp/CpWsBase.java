package com.sidru.sidru_api.cp;

import com.sidru.sidru_api.iam.domain.model.valueobjects.SocialProvider;
import com.sidru.sidru_api.iam.infrastructure.persistence.jpa.repositories.SocialIdentityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

/** Utilidades de los CP del login social (spec sidru-web-social): tokens falsos y llamadas a los endpoints. */
abstract class CpWsBase extends CpBaseTest {

    @Autowired protected SocialIdentityRepository socialIdentityRepository;

    protected static String uniqueEmail(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
    }

    protected static String uniqueSub() {
        return "sub-" + UUID.randomUUID();
    }

    protected static String googleToken(String sub, String email, boolean verified, String name) {
        return FakeIdTokenVerifier.token(SocialProvider.GOOGLE, sub, email, verified, name);
    }

    protected static String appleToken(String sub, String email, boolean verified) {
        return FakeIdTokenVerifier.token(SocialProvider.APPLE, sub, email, verified, null);
    }

    protected ResultActions google(String idToken) throws Exception {
        return mockMvc.perform(apiPost("/authentication/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("idToken", idToken))));
    }

    protected ResultActions apple(String identityToken, String fullName) throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("identityToken", identityToken);
        if (fullName != null) body.put("fullName", fullName);
        return mockMvc.perform(apiPost("/authentication/apple")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    protected Map<?, ?> bodyOf(ResultActions result) throws Exception {
        return objectMapper.readValue(result.andReturn().getResponse().getContentAsString(), Map.class);
    }

    protected long idOf(Map<?, ?> body) {
        return ((Number) body.get("id")).longValue();
    }
}
