package com.hackaton.ulibre.pacientes;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pacientes/{pacienteId}/alergias")
@Tag(name = "Pacientes")
public class AlergiasPacienteController {

    private final AlergiaPacienteService servicio;

    public AlergiasPacienteController(AlergiaPacienteService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PACIENTES_VER')")
    @Operation(summary = "Alergias del paciente (filtro opcional por activo)")
    public List<AlergiaResponse> listar(@PathVariable UUID pacienteId,
            @RequestParam(required = false) Boolean activo) {
        return servicio.listar(pacienteId, activo);
    }

    @GetMapping("/{alergiaId}")
    @PreAuthorize("hasAuthority('PACIENTES_VER')")
    @Operation(summary = "Alergia del paciente por id")
    public AlergiaResponse obtener(@PathVariable UUID pacienteId, @PathVariable UUID alergiaId) {
        return servicio.obtener(pacienteId, alergiaId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PACIENTES_GESTIONAR')")
    @Operation(summary = "Registra una alergia del paciente")
    public ResponseEntity<AlergiaResponse> crear(@PathVariable UUID pacienteId,
            @Valid @RequestBody AlergiaRequest datos) {
        AlergiaResponse creada = servicio.crear(pacienteId, datos);
        return ResponseEntity.created(URI.create("/api/pacientes/" + pacienteId + "/alergias/" + creada.id()))
                .body(creada);
    }

    @PutMapping("/{alergiaId}")
    @PreAuthorize("hasAuthority('PACIENTES_GESTIONAR')")
    @Operation(summary = "Actualiza una alergia del paciente")
    public AlergiaResponse actualizar(@PathVariable UUID pacienteId, @PathVariable UUID alergiaId,
            @Valid @RequestBody AlergiaRequest datos) {
        return servicio.actualizar(pacienteId, alergiaId, datos);
    }

    @DeleteMapping("/{alergiaId}")
    @PreAuthorize("hasAuthority('PACIENTES_GESTIONAR')")
    @Operation(summary = "Desactiva una alergia (las copias ya hechas en cirugías no cambian)")
    public ResponseEntity<Void> desactivar(@PathVariable UUID pacienteId, @PathVariable UUID alergiaId) {
        servicio.desactivar(pacienteId, alergiaId);
        return ResponseEntity.noContent().build();
    }
}
