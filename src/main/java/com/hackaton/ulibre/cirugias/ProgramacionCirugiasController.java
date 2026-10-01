package com.hackaton.ulibre.cirugias;

import java.net.URI;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Programación de cirugías. La lista del día (GET /api/cirugias) está en CirugiasController. */
@RestController
@RequestMapping("/api/cirugias")
@Tag(name = "Cirugías")
public class ProgramacionCirugiasController {

    private final ProgramacionCirugiasService servicio;

    public ProgramacionCirugiasController(ProgramacionCirugiasService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CIRUGIAS_PROGRAMAR')")
    @Operation(summary = "Programa una cirugía a partir de una solicitud APROBADA (la solicitud pasa a PROGRAMADA)")
    public ResponseEntity<ProgramacionCirugiaResponse> programar(@Valid @RequestBody ProgramarCirugiaRequest datos) {
        ProgramacionCirugiaResponse creada = servicio.programar(datos);
        return ResponseEntity.created(URI.create("/api/cirugias/" + creada.id())).body(creada);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CIRUGIAS_VER')")
    @Operation(summary = "Detalle de programación de una cirugía")
    public ProgramacionCirugiaResponse obtener(@PathVariable UUID id) {
        return servicio.detalle(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CIRUGIAS_PROGRAMAR')")
    @Operation(summary = "Reprograma quirófano, horario, coordinador y notas (solo PROGRAMADA, PREPARACION o LISTA)")
    public ProgramacionCirugiaResponse reprogramar(@PathVariable UUID id,
            @Valid @RequestBody ReprogramarCirugiaRequest datos) {
        return servicio.reprogramar(id, datos);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('CIRUGIAS_PROGRAMAR')")
    @Operation(summary = "Cambia el estado (avanza un paso, o cancela/suspende con motivo antes de EN_CIRUGIA)")
    public ProgramacionCirugiaResponse cambiarEstado(@PathVariable UUID id,
            @Valid @RequestBody CambioEstadoCirugiaRequest datos) {
        return servicio.cambiarEstado(id, datos);
    }
}
