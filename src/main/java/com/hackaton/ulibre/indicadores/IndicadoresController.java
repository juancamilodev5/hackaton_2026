package com.hackaton.ulibre.indicadores;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import com.hackaton.ulibre.comun.ReglaNegocioException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IndicadoresController {

    private final IndicadoresConsultas consultas;
    private final Clock clock;

    public IndicadoresController(IndicadoresConsultas consultas, Clock clock) {
        this.consultas = consultas;
        this.clock = clock;
    }

    /** REPEATABLE READ: todas las secciones se calculan sobre la misma foto. */
    @GetMapping("/api/indicadores")
    @PreAuthorize("hasAuthority('CALIDAD_VER')")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Tag(name = "Indicadores")
    @Operation(summary = "Indicadores de calidad de las cirugías con inicio programado en el rango (por defecto, últimos 30 días)")
    public IndicadoresResponse indicadores(
            @Parameter(description = "Fecha YYYY-MM-DD (inclusive)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Fecha YYYY-MM-DD (inclusive)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        LocalDate fin = hasta == null ? LocalDate.now(clock) : hasta;
        LocalDate inicio = desde == null ? fin.minusDays(29) : desde;
        if (inicio.isAfter(fin)) {
            throw new ReglaNegocioException("La fecha desde no puede ser posterior a hasta");
        }
        return consultas.indicadores(inicio, fin);
    }

    @GetMapping("/api/cirugias/{cirugiaId}/trazabilidad")
    @PreAuthorize("hasAnyAuthority('CALIDAD_VER', 'AUDITORIA_VER', 'TABLERO_VER')")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Tag(name = "Trazabilidad")
    @Operation(summary = "Hitos vigentes, tiempos medidos, conteos de alertas e incidentes y timeline de eventos de la cirugía")
    public TrazabilidadResponse trazabilidad(@PathVariable UUID cirugiaId) {
        return consultas.trazabilidad(cirugiaId);
    }
}
