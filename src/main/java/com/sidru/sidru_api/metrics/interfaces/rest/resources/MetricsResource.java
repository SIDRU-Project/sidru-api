package com.sidru.sidru_api.metrics.interfaces.rest.resources;

import java.time.LocalDate;
import java.util.List;

/**
 * Metricas globales de la plataforma e impacto ambiental estimado (US-36), opcionalmente
 * acotadas a un rango de fechas (CP044). Las cifras de impacto son estimaciones con
 * factores configurables, no medidas exactas.
 *
 * {@code from}/{@code to} son null cuando no se aplico filtro (metricas historicas totales).
 */
public record MetricsResource(
        LocalDate from,
        LocalDate to,
        long totalSessions,
        long confirmedSessions,
        long capsRecycled,
        double weightKg,
        long ctcMinted,
        long registeredUsers,
        long activeUsers,
        long activeBins,
        ImpactResource impact,
        List<BinRankingResource> topBins) {

    public record ImpactResource(
            double co2AvoidedKg,
            double energySavedKwh) {
    }

    /**
     * Fila del ranking de dispositivos.
     *
     * @param smartBinId identificador del Smart Bin
     * @param deviceCode codigo legible del bin (p. ej. "BIN-001"); null si ya no existe
     * @param sessions   sesiones confirmadas en la ventana
     * @param caps       tapas acumuladas
     * @param weightKg   peso acumulado en kilogramos
     */
    public record BinRankingResource(
            Long smartBinId,
            String deviceCode,
            long sessions,
            long caps,
            double weightKg) {
    }
}
