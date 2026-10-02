package com.sidru.sidru_api.iam.application.internal.outboundservices.social;

import com.sidru.sidru_api.iam.domain.model.valueobjects.SocialProvider;

/** Puerto que verifica el ID token de un proveedor externo (firma, emisor, audiencia y vigencia). */
public interface IdTokenVerifier {

    /** true si el proveedor está configurado (tiene client IDs). */
    boolean isEnabled(SocialProvider provider);

    /**
     * @throws com.sidru.sidru_api.iam.domain.model.exceptions.InvalidSocialTokenException si el token no es válido
     * @throws com.sidru.sidru_api.iam.domain.model.exceptions.SocialProviderUnavailableException si no se pueden obtener las claves
     */
    VerifiedIdentity verify(SocialProvider provider, String token);
}
