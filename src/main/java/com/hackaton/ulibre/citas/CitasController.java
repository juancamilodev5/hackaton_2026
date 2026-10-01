package com.hackaton.ulibre.citas;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

import com.hackaton.ulibre.comun.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/citas")
@Tag(name = "Citas")
public class CitasController {

    private final CitaService servicio;

    public CitasController(CitaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CITAS_VER')")
    @Operation(summary = "Citas paginadas, ordenadas por fecha programada")
    public Pagina<CitaResponse> listar(
            @RequestParam(required = false) UUID pacienteId,
            @Parameter(description = "Id del perfil profesional del médico")
            @RequestParam(required = false) UUID medicoId,
            @RequestParam(required = false) UUID especialidadId,
            @RequestParam(required = false) EstadoCita estado,
            @Parameter(description = "Fecha YYYY-MM-DD (incluida)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Fecha YYYY-MM-DD (incluida)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(new CitasConsultas.Filtro(pacienteId, medicoId, especialidadId, estado, desde, hasta),
                pagina, tamano);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CITAS_VER')")
    @Operation(summary = "Cita por id")
    public CitaResponse obtener(@PathVariable UUID id) {
        return servicio.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CITAS_GESTIONAR')")
    @Operation(summary = "Agenda una cita (estado SOLICITADA; el médico debe tener la especialidad)")
    public ResponseEntity<CitaResponse> crear(@Valid @RequestBody CitaRequest datos) {
        CitaResponse creada = servicio.crear(datos);
        return ResponseEntity.created(URI.create("/api/citas/" + creada.id())).body(creada);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CITAS_GESTIONAR')")
    @Operation(summary = "Edita o reprograma una cita SOLICITADA o CONFIRMADA")
    public CitaResponse actualizar(@PathVariable UUID id, @Valid @RequestBody CitaRequest datos) {
        return servicio.actualizar(id, datos);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('CITAS_GESTIONAR')")
    @Operation(summary = "Cambia el estado: SOLICITADA→CONFIRMADA|CANCELADA, CONFIRMADA→COMPLETADA|CANCELADA|NO_ASISTIO")
    public CitaResponse cambiarEstado(@PathVariable UUID id, @Valid @RequestBody CambioEstadoCitaRequest datos) {
        return servicio.cambiarEstado(id, datos.estado());
    }
}
