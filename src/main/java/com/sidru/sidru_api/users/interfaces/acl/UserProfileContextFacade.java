package com.sidru.sidru_api.users.interfaces.acl;

public interface UserProfileContextFacade {

    /**
     * Creates a user profile for a newly-registered user.
     * @return the profile id, or 0L if creation failed
     */
    Long createProfile(Long userId, String fullName, String phone, String district);

    /** Adds points and caps to a user's profile after a successful recycling session. */
    Long addPointsAndCaps(Long userId, int points, int caps);

    /** Subtracts points from a user when redeeming a reward. */
    Long subtractPoints(Long userId, int points);

    Boolean existsByUserId(Long userId);

    Integer fetchTotalPointsByUserId(Long userId);

    /** Total de usuarios con perfil (métricas, US-36). */
    long countUsers();
}
