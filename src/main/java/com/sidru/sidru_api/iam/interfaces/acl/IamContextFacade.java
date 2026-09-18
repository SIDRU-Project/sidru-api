package com.sidru.sidru_api.iam.interfaces.acl;

/**
 * ACL del contexto IAM: deja que otros bounded contexts consulten usuarios
 * sin depender de sus internals.
 */
public interface IamContextFacade {

    /** userId por email, o 0L si no existe. */
    Long fetchUserIdByEmail(String email);

    /** email por userId, o cadena vacía si no existe. */
    String fetchEmailByUserId(Long userId);

    Boolean checkUserExistsByUserId(Long userId);
}
