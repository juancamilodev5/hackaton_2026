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
import org.springframework.web.bind.annotation.RestController;

/** Subrecursos de configuración de un procedimiento. Las asociaciones se crean con PUT idempotente. */
@RestController
@RequestMapping("/api/procedimientos/{id}")
@Tag(name = "Procedimientos")
public class ProcedimientoConfiguracionController {

    private final ProcedimientoService servicio;

    public ProcedimientoConfiguracionController(ProcedimientoService servicio) {
        this.servicio = servicio;
    }

    // ------------------------------------------------------------------ especialidades

    @PutMapping("/especialidades/{especialidadId}")
    @PreAuthorize("hasAuthority('CATALOGOS_GESTIONAR')")
    @Operation(summary = "Habilita el procedimiento para una especialidad (idempotente)")
    public ResponseEntity<Void> agregarEspecialidad(@PathVariable UUID id, @PathVariable UUID especialidadId) {
        servicio.agregarEspecialidad(id, especialidadId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/especialidades/{especialidadId}")
    @PreAuthorize("hasAuthority('CATALOGOS_GESTIONAR')")
    @Operation(summary = "Quita una especialidad (422 si alguna solicitud la usa)")
    public ResponseEntity<Void> quitarEspecialidad(@PathVariable UUID id, @PathVariable UUID especialidadId) {
        servicio.quitarEspecialidad(id, especialidadId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ roles predeterminados

    @GetMapping("/roles-predeterminados")
    @PreAuthorize("hasAuthority('CATALOGOS_VER')")
    @Operation(summary = "Roles clínicos sugeridos para el procedimiento")
    public List<RolPredeterminadoResponse> listarRoles(@PathVariable UUID id) {
        return servicio.listarRoles(id);
    }

    @PostMapping("/roles-predeterminados")
    @PreAuthorize("hasAuthority('CATALOGOS_GESTIONAR')")
    @Operation(summary = "Agrega un rol sugerido")
    public ResponseEntity<RolPredeterminadoResponse> crearRol(@PathVariable UUID id,
            @Valid @RequestBody RolPredeterminadoRequest datos) {
        RolPredeterminadoResponse creado = servicio.crearRol(id, datos);
        return ResponseEntity.created(URI.create("/api/procedimientos/" + id + "/roles-predeterminados/" + creado.id()))
                .body(creado);
    }

    @PutMapping("/roles-predeterminados/{rolPredId}")
    @PreAuthorize("hasAuthority('CATALOGOS_GESTIONAR')")
    @Operation(summary = "Actualiza un rol sugerido")
    public RolPredeterminadoResponse actualizarRol(@PathVariable UUID id, @PathVariable UUID rolPredId,
            @Valid @RequestBody RolPredeterminadoRequest datos) {
        return servicio.actualizarRol(id, rolPredId, datos);
    }

    @DeleteMapping("/roles-predeterminados/{rolPredId}")
    @PreAuthorize("hasAuthority('CATALOGOS_GESTIONAR')")
    @Operation(summary = "Elimina un rol sugerido (es configuración: no afecta solicitudes existentes)")
    public ResponseEntity<Void> borrarRol(@PathVariable UUID id, @PathVariable UUID rolPredId) {
        servicio.borrarRol(id, rolPredId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ plantillas de checklist

    @PutMapping("/plantillas-checklist/{plantillaId}")
    @PreAuthorize("hasAuthority('PROTOCOLOS_GESTIONAR')")
    @Operation(summary = "Asocia una plantilla de checklist; esPredeterminada=true reemplaza a la anterior")
    public ResponseEntity<Void> asociarPlantilla(@PathVariable UUID id, @PathVariable UUID plantillaId,
            @RequestBody(required = false) PlantillaProcedimientoRequest datos) {
        servicio.asociarPlantilla(id, plantillaId, datos);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/plantillas-checklist/{plantillaId}")
    @PreAuthorize("hasAuthority('PROTOCOLOS_GESTIONAR')")
    @Operation(summary = "Desasocia una plantilla de checklist")
    public ResponseEntity<Void> quitarPlantilla(@PathVariable UUID id, @PathVariable UUID plantillaId) {
        servicio.quitarPlantilla(id, plantillaId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ instrumental predeterminado

    @PutMapping("/sets-predeterminados/{setId}")
    @PreAuthorize("hasAuthority('INSTRUMENTAL_GESTIONAR')")
    @Operation(summary = "Agrega o actualiza un set instrumental predeterminado")
    public ResponseEntity<Void> ponerSet(@PathVariable UUID id, @PathVariable UUID setId,
            @Valid @RequestBody PredeterminadoRequest datos) {
        servicio.ponerSet(id, setId, datos);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sets-predeterminados/{setId}")
    @PreAuthorize("hasAuthority('INSTRUMENTAL_GESTIONAR')")
    @Operation(summary = "Quita un set predeterminado (no afecta el instrumental ya copiado a cirugías)")
    public ResponseEntity<Void> quitarSet(@PathVariable UUID id, @PathVariable UUID setId) {
        servicio.quitarSet(id, setId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/instrumentos-predeterminados/{instrumentoId}")
    @PreAuthorize("hasAuthority('INSTRUMENTAL_GESTIONAR')")
    @Operation(summary = "Agrega o actualiza un instrumento requerido directamente (fuera de sets)")
    public ResponseEntity<Void> ponerInstrumento(@PathVariable UUID id, @PathVariable UUID instrumentoId,
            @Valid @RequestBody PredeterminadoRequest datos) {
        servicio.ponerInstrumento(id, instrumentoId, datos);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/instrumentos-predeterminados/{instrumentoId}")
    @PreAuthorize("hasAuthority('INSTRUMENTAL_GESTIONAR')")
    @Operation(summary = "Quita un instrumento predeterminado")
    public ResponseEntity<Void> quitarInstrumento(@PathVariable UUID id, @PathVariable UUID instrumentoId) {
        servicio.quitarInstrumento(id, instrumentoId);
        return ResponseEntity.noContent().build();
    }
}
