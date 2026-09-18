package com.sidru.sidru_api.sessions.domain.model.valueobjects;

/**
 * Actividad agregada de un Smart Bin en una ventana de tiempo (CP044 / US-36).
 * Valor de solo lectura: alimenta el ranking de dispositivos del dashboard.
 *
 * @param smartBinId  identificador del Smart Bin
 * @param sessions    sesiones confirmadas en la ventana
 * @param caps        tapas acumuladas en esas sesiones
 * @param weightGrams peso acumulado en gramos
 */
public record BinActivity(Long smartBinId, long sessions, long caps, double weightGrams) {
}
