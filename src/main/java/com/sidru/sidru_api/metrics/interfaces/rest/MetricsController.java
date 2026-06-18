package com.sidru.sidru_api.metrics.interfaces.rest;

import com.sidru.sidru_api.metrics.application.internal.queryservices.MetricsService;
import com.sidru.sidru_api.metrics.interfaces.rest.resources.MetricsResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Métricas globales de la plataforma + impacto ambiental (US-36). Visible para cualquier
 * usuario autenticado: alimenta la pantalla de impacto de la app y el monitoreo de admin.
 */
@RestController
@RequestMapping(value = "/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Metrics", description = "Métricas globales de uso e impacto ambiental")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping
    @Operation(summary = "Métricas globales e impacto ambiental estimado")
    public ResponseEntity<MetricsResource> getMetrics() {
        return ResponseEntity.ok(metricsService.gather());
    }
}
