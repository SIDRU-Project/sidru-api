package com.sidru.sidru_api.metrics.application.internal.queryservices;

import com.sidru.sidru_api.devices.interfaces.acl.DevicesContextFacade;
import com.sidru.sidru_api.metrics.interfaces.rest.resources.MetricsResource;
import com.sidru.sidru_api.sessions.interfaces.acl.SessionsContextFacade;
import com.sidru.sidru_api.users.interfaces.acl.UserProfileContextFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Agrega métricas globales de uso e impacto ambiental (US-36). Es un contexto de
 * REPORTE (solo lectura) que lee a través de las fachadas ACL de sessions/users/devices;
 * no toca sus entidades ni repositorios directamente. El impacto se deriva del peso
 * reciclado con factores configurables (estimaciones de literatura de reciclaje).
 */
@Service
public class MetricsService {

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

    public MetricsResource gather() {
        long totalSessions = sessionsFacade.countAllSessions();
        long confirmed = sessionsFacade.countConfirmedSessions();
        long caps = sessionsFacade.sumConfirmedCaps();
        double weightKg = round2(sessionsFacade.sumConfirmedWeightGrams() / 1000.0);
        long ctc = sessionsFacade.sumConfirmedPoints();
        long users = usersFacade.countUsers();
        long bins = devicesFacade.countBins();

        var impact = new MetricsResource.ImpactResource(
                round2(weightKg * co2KgPerKgPlastic),
                round2(weightKg * energyKwhPerKgPlastic));

        return new MetricsResource(totalSessions, confirmed, caps, weightKg, ctc, users, bins, impact);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
