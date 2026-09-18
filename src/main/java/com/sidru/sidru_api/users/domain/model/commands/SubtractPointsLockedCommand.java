package com.sidru.sidru_api.users.domain.model.commands;

/**
 * Débito de puntos bajo lock pesimista sobre el perfil (retiro, spec sidru-mainnet).
 * A diferencia de {@link SubtractPointsCommand} (canje de recompensas), este lee con
 * {@code findByUserIdForUpdate} para serializar retiros concurrentes del mismo usuario.
 */
public record SubtractPointsLockedCommand(Long userId, int points) {
}
