package com.sidru.sidru_api.sessions.interfaces.acl;

import com.sidru.sidru_api.sessions.domain.model.valueobjects.BinActivity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Puerto de solo lectura de sessions hacia otros contextos (p.ej. metrics): expone
 * agregados sin filtrar entidades ni repositorio (US-36).
 *
 * Las variantes con rango [from, to] alimentan el dashboard operativo filtrable
 * por fechas y su exportacion (CP044).
 */
public interface SessionsContextFacade {
    long countAllSessions();
    long countConfirmedSessions();
    long sumConfirmedCaps();
    double sumConfirmedWeightGrams();
    long sumConfirmedPoints();

    long countAllSessions(LocalDateTime from, LocalDateTime to);
    long countConfirmedSessions(LocalDateTime from, LocalDateTime to);
    long sumConfirmedCaps(LocalDateTime from, LocalDateTime to);
    double sumConfirmedWeightGrams(LocalDateTime from, LocalDateTime to);
    long sumConfirmedPoints(LocalDateTime from, LocalDateTime to);

    /** Ciudadanos distintos con al menos una sesion confirmada en la ventana. */
    long countActiveUsers(LocalDateTime from, LocalDateTime to);

    /** Ranking de Smart Bins por sesiones confirmadas en la ventana, de mayor a menor. */
    List<BinActivity> rankBins(LocalDateTime from, LocalDateTime to, int limit);
}
