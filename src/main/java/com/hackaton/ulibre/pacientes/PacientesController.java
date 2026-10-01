package com.hackaton.ulibre.pacientes;

import java.net.URI;
import java.util.UUID;

import com.hackaton.ulibre.comun.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/pacientes")
@Tag(name = "Pacientes")
public class PacientesController {

    private final PacienteService servicio;

    public PacientesController(PacienteService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PACIENTES_VER')")
    @Operation(summary = "Pacientes paginados, ordenados por apellidos y nombres")
    public Pagina<PacienteResumen> listar(
            @Parameter(description = "Busca en nombres, apellidos o número de documento")
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(q, pagina, tamano);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PACIENTES_VER')")
    @Operation(summary = "Paciente con edad calculada y alergias activas")
    public PacienteResponse obtener(@PathVariable UUID id) {
        return servicio.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PACIENTES_GESTIONAR')")
    @Operation(summary = "Registra un paciente (documento único por tipo)")
    public ResponseEntity<PacienteResponse> crear(@Valid @RequestBody PacienteRequest datos) {
        PacienteResponse creado = servicio.crear(datos);
        return ResponseEntity.created(URI.create("/api/pacientes/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PACIENTES_GESTIONAR')")
    @Operation(summary = "Actualiza los datos de un paciente")
    public PacienteResponse actualizar(@PathVariable UUID id, @Valid @RequestBody PacienteRequest datos) {
        return servicio.actualizar(id, datos);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PACIENTES_GESTIONAR')")
    @Operation(summary = "Borra un paciente sin histórico (con citas o solicitudes responde 422)")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
