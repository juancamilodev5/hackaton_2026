package com.hackaton.ulibre.cirugias;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cirugias")
@Tag(name = "Cirugías")
public class CirugiasController {

    private final CirugiasConsultas consultas;
    private final Clock clock;

    public CirugiasController(CirugiasConsultas consultas, Clock clock) {
        this.consultas = consultas;
        this.clock = clock;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CIRUGIAS_VER')")
    @Transactional(readOnly = true)
    @Operation(summary = "Cirugías programadas para una fecha (por defecto hoy, hora de Colombia)")
    public List<CirugiaResumen> listar(
            @Parameter(description = "Fecha YYYY-MM-DD")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return consultas.listar(fecha == null ? LocalDate.now(clock) : fecha);
    }
}
