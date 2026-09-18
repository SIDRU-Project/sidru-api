package com.sidru.sidru_api.shared.interfaces.rest.resources;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envoltorio de respuesta paginada, comun a cualquier contexto (CP016 / US-22).
 * Se expone un contrato propio y estable en vez del {@code Page} de Spring Data,
 * que arrastra metadatos internos y no es estable entre versiones.
 *
 * @param content       elementos de la pagina actual
 * @param page          indice de la pagina (base 0)
 * @param size          tamano de pagina solicitado
 * @param totalElements total de elementos que cumplen el criterio
 * @param totalPages    total de paginas disponibles
 */
public record PagedResource<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    /** Construye el recurso paginado mapeando cada entidad de la pagina con {@code mapper}. */
    public static <E, R> PagedResource<R> from(Page<E> source, Function<E, R> mapper) {
        return new PagedResource<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages());
    }
}
