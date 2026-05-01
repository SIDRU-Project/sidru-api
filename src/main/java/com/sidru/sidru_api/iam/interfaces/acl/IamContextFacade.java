package com.sidru.sidru_api.iam.interfaces.acl;

/**
 * IamContextFacade — ACL entry point so other bounded contexts can interact
 * with the IAM context without depending on its internals.
 */
public interface IamContextFacade {

    /** Returns the userId by email, or 0L if not found. */
    Long fetchUserIdByEmail(String email);

    /** Returns the email by userId, or empty string if not found. */
    String fetchEmailByUserId(Long userId);

    Boolean checkUserExistsByUserId(Long userId);
}
