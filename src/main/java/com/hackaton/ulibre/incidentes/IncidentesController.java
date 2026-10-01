package com.hackaton.ulibre.incidentes;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cirugias/{cirugiaId}/incidentes")
@Tag(name = "Incidentes")
public class IncidentesController {

    private final IncidentesService servicio;

    public IncidentesController(IncidentesService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TABLERO_VER')")
    @Operation(summary = "Incidentes de la cirugía, del más reciente al más antiguo")
    public List<IncidenteResponse> listar(@PathVariable UUID cirugiaId) {
        return servicio.listar(cirugiaId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INCIDENTES_GESTIONAR')")
    @Operation(summary = "Reporta un incidente (queda ABIERTO, reportado por el usuario autenticado)")
    public ResponseEntity<IncidenteResponse> reportar(@PathVariable UUID cirugiaId,
            @Valid @RequestBody IncidenteRequest datos) {
        IncidenteResponse creado = servicio.reportar(cirugiaId, datos);
        return ResponseEntity.created(URI.create("/api/cirugias/" + cirugiaId + "/incidentes/" + creado.id()))
                .body(creado);
    }

    @PutMapping("/{incidenteId}")
    @PreAuthorize("hasAuthority('INCIDENTES_GESTIONAR')")
    @Operation(summary = "Actualiza un incidente (RESUELTO registra quién y cuándo; no se borran, se cancelan)")
    public IncidenteResponse actualizar(@PathVariable UUID cirugiaId, @PathVariable UUID incidenteId,
            @Valid @RequestBody ActualizarIncidenteRequest datos) {
        return servicio.actualizar(cirugiaId, incidenteId, datos);
    }
}
