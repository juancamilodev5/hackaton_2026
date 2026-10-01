package com.hackaton.ulibre.cirugias;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cirugias/{cirugiaId}")
@Tag(name = "Asignación de personal")
public class AsignacionesController {

    private final AsignacionesService servicio;

    public AsignacionesController(AsignacionesService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/asignaciones")
    @PreAuthorize("hasAuthority('CIRUGIAS_VER')")
    @Operation(summary = "Asignaciones de la cirugía, incluidas las rechazadas y canceladas")
    public List<AsignacionResponse> listar(@PathVariable UUID cirugiaId) {
        return servicio.listar(cirugiaId);
    }

    @PostMapping("/asignaciones")
    @PreAuthorize("hasAuthority('PERSONAL_ASIGNAR')")
    @Operation(summary = "Asigna un profesional a un requerimiento (reactiva su asignación si estaba rechazada o cancelada)")
    public ResponseEntity<AsignacionResponse> asignar(@PathVariable UUID cirugiaId,
            @Valid @RequestBody AsignacionRequest datos) {
        AsignacionResponse asignacion = servicio.asignar(cirugiaId, datos);
        return ResponseEntity
                .created(URI.create("/api/cirugias/" + cirugiaId + "/asignaciones/" + asignacion.id()))
                .body(asignacion);
    }

    @PatchMapping("/asignaciones/{asignacionId}/estado")
    @PreAuthorize("hasAuthority('PERSONAL_ASIGNAR')")
    @Operation(summary = "Confirma, rechaza o cancela una asignación vigente")
    public AsignacionResponse cambiarEstado(@PathVariable UUID cirugiaId, @PathVariable UUID asignacionId,
            @Valid @RequestBody CambioEstadoAsignacionRequest datos) {
        return servicio.cambiarEstado(cirugiaId, asignacionId, datos.estado());
    }

    @PutMapping("/operador-tablero")
    @PreAuthorize("hasAuthority('PERSONAL_ASIGNAR')")
    @Operation(summary = "Designa el operador del tablero (reemplaza al actual)")
    public AsignacionResponse designarOperador(@PathVariable UUID cirugiaId,
            @Valid @RequestBody OperadorTableroRequest datos) {
        return servicio.designarOperador(cirugiaId, datos.asignacionId());
    }

    @DeleteMapping("/operador-tablero")
    @PreAuthorize("hasAuthority('PERSONAL_ASIGNAR')")
    @Operation(summary = "Quita el operador del tablero")
    public ResponseEntity<Void> quitarOperador(@PathVariable UUID cirugiaId) {
        servicio.quitarOperador(cirugiaId);
        return ResponseEntity.noContent().build();
    }
}
