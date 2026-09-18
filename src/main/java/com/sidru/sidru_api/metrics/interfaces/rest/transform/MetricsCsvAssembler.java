package com.sidru.sidru_api.metrics.interfaces.rest.transform;

import com.sidru.sidru_api.metrics.interfaces.rest.resources.MetricsResource;

import java.util.Locale;

/**
 * Serializa las metricas del dashboard a CSV (CP044 / US-36).
 *
 * El archivo trae dos bloques separados por una linea en blanco: primero los indicadores
 * globales como pares metrica/valor y luego el ranking de dispositivos como tabla. Los
 * decimales usan punto (Locale.ROOT) para que el archivo sea portable entre configuraciones
 * regionales.
 */
public final class MetricsCsvAssembler {

    private MetricsCsvAssembler() {}

    public static String toCsv(MetricsResource m) {
        StringBuilder csv = new StringBuilder();

        csv.append("metrica,valor\n");
        csv.append("desde,").append(m.from() == null ? "" : m.from()).append('\n');
        csv.append("hasta,").append(m.to() == null ? "" : m.to()).append('\n');
        csv.append("sesiones_totales,").append(m.totalSessions()).append('\n');
        csv.append("sesiones_confirmadas,").append(m.confirmedSessions()).append('\n');
        csv.append("tapas_recicladas,").append(m.capsRecycled()).append('\n');
        csv.append("peso_kg,").append(num(m.weightKg())).append('\n');
        csv.append("ctc_emitidos,").append(m.ctcMinted()).append('\n');
        csv.append("usuarios_registrados,").append(m.registeredUsers()).append('\n');
        csv.append("usuarios_activos,").append(m.activeUsers()).append('\n');
        csv.append("bins_registrados,").append(m.activeBins()).append('\n');
        csv.append("co2_evitado_kg,").append(num(m.impact().co2AvoidedKg())).append('\n');
        csv.append("energia_ahorrada_kwh,").append(num(m.impact().energySavedKwh())).append('\n');

        csv.append('\n');
        csv.append("smart_bin_id,device_code,sesiones,tapas,peso_kg\n");
        for (var bin : m.topBins()) {
            csv.append(bin.smartBinId()).append(',')
               .append(escape(bin.deviceCode())).append(',')
               .append(bin.sessions()).append(',')
               .append(bin.caps()).append(',')
               .append(num(bin.weightKg())).append('\n');
        }
        return csv.toString();
    }

    private static String num(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** Entrecomilla solo si hace falta; el device_code es libre y podria traer comas. */
    private static String escape(String value) {
        if (value == null) return "";
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
