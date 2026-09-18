package com.sidru.sidru_api.shared.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Access log de una línea por request: método, ruta, código y duración.
 *
 * <p>Va con prioridad máxima ({@link Ordered#HIGHEST_PRECEDENCE}) para envolver toda la cadena
 * de filtros: así mide el tiempo total y ve el código final, incluidos los 401/403 de Spring
 * Security. El nivel se elige por código: 5xx error, 4xx warn, resto info.
 *
 * <p>No registra cabeceras ni cuerpos para no fugar el JWT u otros datos sensibles a los logs.
 * Omite Swagger/OpenAPI y estáticos.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger("com.sidru.access");

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.contains("/swagger")
                || uri.contains("/v3/api-docs")
                || uri.contains("/webjars")
                || uri.endsWith("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = System.currentTimeMillis() - start;
            int status = response.getStatus();
            String query = request.getQueryString();
            String path = query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;

            if (status >= 500) {
                LOGGER.error("HTTP {} {} {} ({}ms)", status, request.getMethod(), path, elapsedMs);
            } else if (status >= 400) {
                LOGGER.warn("HTTP {} {} {} ({}ms)", status, request.getMethod(), path, elapsedMs);
            } else {
                LOGGER.info("HTTP {} {} {} ({}ms)", status, request.getMethod(), path, elapsedMs);
            }
        }
    }
}
