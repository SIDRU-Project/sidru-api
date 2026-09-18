package com.sidru.sidru_api.metrics.application.internal.queryservices;

import com.sidru.sidru_api.devices.interfaces.acl.DevicesContextFacade;
import com.sidru.sidru_api.metrics.interfaces.rest.resources.MetricsResource;
import com.sidru.sidru_api.sessions.interfaces.acl.SessionsContextFacade;
import com.sidru.sidru_api.users.interfaces.acl.UserProfileContextFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Agrega métricas globales de uso e impacto ambiental (US-36). Contexto de solo lectura:
 * lee a través de las fachadas ACL de sessions/users/devices, sin tocar sus entidades ni
 * repositorios. El impacto se deriva del peso reciclado con factores configurables.
 *
 * <p>{@link #gather()} devuelve los totales históricos (contrato original que consume la
 * pantalla de impacto de la app). {@link #gather(LocalDate, LocalDate)} acota los mismos
 * indicadores a un rango de fechas y añade usuarios activos y ranking de dispositivos, que
 * es lo que alimenta el dashboard operativo y su exportación (CP044).</p>
 */
@Service
public class MetricsService {

    /** Tope del ranking de dispositivos del dashboard. */
    public static final int TOP_BINS_LIMIT = 10;

    private final SessionsContextFacade sessionsFacade;
    private final UserProfileContextFacade usersFacade;
    private final DevicesContextFacade devicesFacade;

    @Value("${sidru.impact.co2-kg-per-kg-plastic}")
    private double co2KgPerKgPlastic;

    @Value("${sidru.impact.energy-kwh-per-kg-plastic}")
    private double energyKwhPerKgPlastic;

    public MetricsService(SessionsContextFacade sessionsFacade,
                          UserProfileContextFacade usersFacade,
                          DevicesContextFacade devicesFacade) {
        this.sessionsFacade = sessionsFacade;
        this.usersFacade = usersFacade;
        this.devicesFacade = devicesFacade;
    }

    /** Métricas históricas totales, sin filtro de fechas. */
    public MetricsResource gather() {
        return gather(null, null);
    }

    /**
     * Métricas acotadas a [from, to]. Cualquiera de los dos extremos puede ser null:
     * null en {@code from} significa "desde el inicio" y null en {@code to}, "hasta hoy".
     * El rango es inclusivo en ambos extremos (el día {@code to} cuenta completo).
     */
    public MetricsResource gather(LocalDate from, LocalDate to) {
        boolean filtered = from != null || to != null;
        LocalDateTime start = from != null ? from.atStartOfDay() : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime end = to != null ? to.atTime(LocalTime.MAX) : LocalDateTime.now().plusDays(1);

        long totalSessions = filtered
                ? sessionsFacade.countAllSessions(start, end) : sessionsFacade.countAllSessions();
        long confirmed = filtered
                ? sessionsFacade.countConfirmedSessions(start, end) : sessionsFacade.countConfirmedSessions();
        long caps = filtered
                ? sessionsFacade.sumConfirmedCaps(start, end) : sessionsFacade.sumConfirmedCaps();
        double weightGrams = filtered
                ? sessionsFacade.sumConfirmedWeightGrams(start, end) : sessionsFacade.sumConfirmedWeightGrams();
        long ctc = filtered
                ? sessionsFacade.sumConfirmedPoints(start, end) : sessionsFacade.sumConfirmedPoints();

        double weightKg = round2(weightGrams / 1000.0);
        long users = usersFacade.countUsers();
        long bins = devicesFacade.countBins();
        long activeUsers = sessionsFacade.countActiveUsers(start, end);

        List<MetricsResource.BinRankingResource> topBins =
                sessionsFacade.rankBins(start, end, TOP_BINS_LIMIT).stream()
                        .map(b -> new MetricsResource.BinRankingResource(
                                b.smartBinId(),
                                devicesFacade.fetchDeviceCodeById(b.smartBinId()),
                                b.sessions(),
                                b.caps(),
                                round2(b.weightGrams() / 1000.0)))
                        .toList();

        var impact = new MetricsResource.ImpactResource(
                round2(weightKg * co2KgPerKgPlastic),
                round2(weightKg * energyKwhPerKgPlastic));

        return new MetricsResource(from, to, totalSessions, confirmed, caps, weightKg,
                ctc, users, activeUsers, bins, impact, topBins);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
