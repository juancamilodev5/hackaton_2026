package com.hackaton.ulibre.profesionales;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.hackaton.ulibre.comun.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/profesionales")
@Tag(name = "Profesionales")
public class ProfesionalesController {

    private final ProfesionalesService profesionales;
    private final DisponibilidadService disponibilidad;

    public ProfesionalesController(ProfesionalesService profesionales, DisponibilidadService disponibilidad) {
        this.profesionales = profesionales;
        this.disponibilidad = disponibilidad;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROFESIONALES_VER')")
    @Operation(summary = "Profesionales paginados con roles clínicos y especialidades (q busca por nombre o correo)")
    public Pagina<ProfesionalResponse> listar(
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) UUID rolClinicoId,
            @RequestParam(required = false) UUID especialidadId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return profesionales.listar(activo, rolClinicoId, especialidadId, q, pagina, tamano);
    }

    @GetMapping("/disponibles")
    @PreAuthorize("hasAuthority('PROFESIONALES_VER')")
    @Operation(summary = "Profesionales activos con el rol (y especialidad) libres en el rango: sin bloque "
            + "NO_DISPONIBLE ni otra cirugía vigente solapada. Orientativo: la base valida al asignar")
    public List<ProfesionalDisponibleResponse> disponibles(
            @Parameter(description = "Inicio, hora de Colombia (YYYY-MM-DDTHH:mm)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @Parameter(description = "Fin (exclusivo), hora de Colombia")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam UUID rolClinicoId,
            @RequestParam(required = false) UUID especialidadId) {
        return profesionales.disponibles(desde, hasta, rolClinicoId, especialidadId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PROFESIONALES_VER')")
    @Operation(summary = "Profesional por id")
    public ProfesionalResponse obtener(@PathVariable UUID id) {
        return profesionales.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROFESIONALES_GESTIONAR')")
    @Operation(summary = "Crea el perfil profesional de un usuario (uno por usuario)")
    public ResponseEntity<ProfesionalResponse> crear(@Valid @RequestBody NuevoProfesionalRequest datos) {
        ProfesionalResponse creado = profesionales.crear(datos);
        return ResponseEntity.created(URI.create("/api/profesionales/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PROFESIONALES_GESTIONAR')")
    @Operation(summary = "Actualiza licencia, número profesional y estado activo")
    public ProfesionalResponse actualizar(@PathVariable UUID id, @Valid @RequestBody ProfesionalRequest datos) {
        return profesionales.actualizar(id, datos);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PROFESIONALES_GESTIONAR')")
    @Operation(summary = "Desactiva un profesional (no se borra: puede tener histórico)")
    public ResponseEntity<Void> desactivar(@PathVariable UUID id) {
        profesionales.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/roles-clinicos")
    @PreAuthorize("hasAuthority('PROFESIONALES_GESTIONAR')")
    @Operation(summary = "Reemplaza los roles clínicos que puede desempeñar")
    public ProfesionalResponse reemplazarRolesClinicos(@PathVariable UUID id, @Valid @RequestBody IdsRequest datos) {
        return profesionales.reemplazarRolesClinicos(id, datos.ids());
    }

    @PutMapping("/{id}/especialidades")
    @PreAuthorize("hasAuthority('PROFESIONALES_GESTIONAR')")
    @Operation(summary = "Reemplaza sus especialidades (422 si una solicitud usa la que se quita)")
    public ProfesionalResponse reemplazarEspecialidades(@PathVariable UUID id, @Valid @RequestBody IdsRequest datos) {
        return profesionales.reemplazarEspecialidades(id, datos.ids());
    }

    @GetMapping("/{id}/disponibilidad")
    @PreAuthorize("hasAuthority('PROFESIONALES_VER')")
    @Operation(summary = "Bloques de disponibilidad que se solapan con [desde, hasta) (sin rango: todos)")
    public List<DisponibilidadResponse> listarDisponibilidad(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        return disponibilidad.listar(id, desde, hasta);
    }

    @PostMapping("/{id}/disponibilidad")
    @PreAuthorize("hasAuthority('DISPONIBILIDAD_GESTIONAR')")
    @Operation(summary = "Registra un bloque de disponibilidad (DISPONIBLE, NO_DISPONIBLE, DE_TURNO)")
    public ResponseEntity<DisponibilidadResponse> crearDisponibilidad(@PathVariable UUID id,
            @Valid @RequestBody DisponibilidadRequest datos) {
        DisponibilidadResponse creado = disponibilidad.crear(id, datos);
        return ResponseEntity.created(URI.create("/api/profesionales/" + id + "/disponibilidad/" + creado.id()))
                .body(creado);
    }

    @PutMapping("/{id}/disponibilidad/{disponibilidadId}")
    @PreAuthorize("hasAuthority('DISPONIBILIDAD_GESTIONAR')")
    @Operation(summary = "Actualiza un bloque de disponibilidad")
    public DisponibilidadResponse actualizarDisponibilidad(@PathVariable UUID id, @PathVariable UUID disponibilidadId,
            @Valid @RequestBody DisponibilidadRequest datos) {
        return disponibilidad.actualizar(id, disponibilidadId, datos);
    }

    @DeleteMapping("/{id}/disponibilidad/{disponibilidadId}")
    @PreAuthorize("hasAuthority('DISPONIBILIDAD_GESTIONAR')")
    @Operation(summary = "Elimina un bloque de disponibilidad")
    public ResponseEntity<Void> eliminarDisponibilidad(@PathVariable UUID id, @PathVariable UUID disponibilidadId) {
        disponibilidad.eliminar(id, disponibilidadId);
        return ResponseEntity.noContent().build();
    }
}
