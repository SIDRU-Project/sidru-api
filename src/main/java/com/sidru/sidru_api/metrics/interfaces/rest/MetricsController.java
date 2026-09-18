package com.sidru.sidru_api.metrics.interfaces.rest;

import com.sidru.sidru_api.metrics.application.internal.queryservices.MetricsService;
import com.sidru.sidru_api.metrics.interfaces.rest.resources.MetricsResource;
import com.sidru.sidru_api.metrics.interfaces.rest.transform.MetricsCsvAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Métricas globales de la plataforma + impacto ambiental (US-36). Visible para cualquier
 * usuario autenticado: alimenta la pantalla de impacto de la app y el monitoreo de admin.
 *
 * <p>Acepta un filtro opcional por rango de fechas ({@code from}/{@code to}, formato
 * ISO {@code yyyy-MM-dd}, ambos extremos inclusivos) y expone la exportación CSV del
 * conjunto filtrado, restringida a administradores (CP044).</p>
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
    @Operation(summary = "Métricas globales e impacto ambiental estimado, filtrables por fechas")
    public ResponseEntity<MetricsResource> getMetrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(metricsService.gather(from, to));
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exporta a CSV el conjunto de métricas correspondiente al filtro aplicado")
    public ResponseEntity<String> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String csv = MetricsCsvAssembler.toCsv(metricsService.gather(from, to));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sidru-metricas.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }
}
