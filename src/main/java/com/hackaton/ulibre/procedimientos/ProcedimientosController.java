package com.hackaton.ulibre.procedimientos;

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
@RequestMapping("/api/procedimientos")
@Tag(name = "Procedimientos")
public class ProcedimientosController {

    private final ProcedimientoService servicio;

    public ProcedimientosController(ProcedimientoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CATALOGOS_VER')")
    @Operation(summary = "Procedimientos, ordenados por nombre (filtros opcionales: activo, especialidadId)")
    public List<ProcedimientoResponse> listar(@RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) UUID especialidadId) {
        return servicio.listar(activo, especialidadId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOGOS_VER')")
    @Operation(summary = "Procedimiento con especialidades, roles, plantillas e instrumental predeterminados")
    public ProcedimientoDetalleResponse obtener(@PathVariable UUID id) {
        return servicio.detalle(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CATALOGOS_GESTIONAR')")
    @Operation(summary = "Crea un procedimiento (código en mayúsculas, único)")
    public ResponseEntity<ProcedimientoResponse> crear(@Valid @RequestBody ProcedimientoRequest datos) {
        ProcedimientoResponse creado = servicio.crear(datos);
        return ResponseEntity.created(URI.create("/api/procedimientos/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOGOS_GESTIONAR')")
    @Operation(summary = "Actualiza un procedimiento")
    public ProcedimientoResponse actualizar(@PathVariable UUID id, @Valid @RequestBody ProcedimientoRequest datos) {
        return servicio.actualizar(id, datos);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOGOS_GESTIONAR')")
    @Operation(summary = "Desactiva un procedimiento (no se borra: puede tener histórico)")
    public ResponseEntity<Void> desactivar(@PathVariable UUID id) {
        servicio.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
