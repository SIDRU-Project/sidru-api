package com.sidru.sidru_api.metrics.interfaces.rest.resources;

/**
 * Métricas globales de la plataforma + impacto ambiental estimado (US-36).
 * Las cifras de impacto son ESTIMACIONES (factores configurables), no medidas exactas.
 */
public record MetricsResource(
        long totalSessions,
        long confirmedSessions,
        long capsRecycled,
        double weightKg,
        long ctcMinted,
        long registeredUsers,
        long activeBins,
        ImpactResource impact) {

    public record ImpactResource(
            double co2AvoidedKg,
            double energySavedKwh) {
    }
}
