package com.sidru.sidru_api.sessions.domain.model.queries;

/**
 * Historial paginado de sesiones de un ciudadano (CP016 / US-22).
 *
 * @param userId ciudadano dueno del historial
 * @param page   indice de pagina (base 0)
 * @param size   tamano de pagina
 */
public record GetRecyclingSessionsByUserIdPagedQuery(Long userId, int page, int size) {

    /** Tamano por defecto y tope duro, para que un `size` enorme no tumbe la consulta. */
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public GetRecyclingSessionsByUserIdPagedQuery {
        if (page < 0) page = 0;
        if (size <= 0) size = DEFAULT_SIZE;
        if (size > MAX_SIZE) size = MAX_SIZE;
    }
}
