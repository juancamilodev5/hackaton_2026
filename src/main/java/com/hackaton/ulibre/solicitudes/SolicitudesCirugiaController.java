package com.hackaton.ulibre.solicitudes;

import java.net.URI;
import java.util.UUID;

import com.hackaton.ulibre.comun.Pagina;
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
@RequestMapping("/api/solicitudes-cirugia")
@Tag(name = "Solicitudes de cirugía")
public class SolicitudesCirugiaController {

    private final SolicitudesService servicio;

    public SolicitudesCirugiaController(SolicitudesService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SOLICITUDES_CIRUGIA_VER')")
    @Operation(summary = "Solicitudes paginadas, más recientes primero (filtros opcionales)")
    public Pagina<SolicitudCirugiaResumen> listar(
            @RequestParam(required = false) EstadoSolicitudCirugia estado,
            @RequestParam(required = false) UUID pacienteId,
            @RequestParam(required = false) UUID medicoId,
            @RequestParam(required = false) UUID procedimientoId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(estado, pacienteId, medicoId, procedimientoId, pagina, tamano);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SOLICITUDES_CIRUGIA_VER')")
    @Operation(summary = "Detalle de una solicitud con sus requerimientos de rol")
    public SolicitudCirugiaResponse obtener(@PathVariable UUID id) {
        return servicio.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SOLICITUDES_CIRUGIA_CREAR')")
    @Operation(summary = "Crea una solicitud en BORRADOR; sin requerimientos se copian los roles predeterminados "
            + "del procedimiento")
    public ResponseEntity<SolicitudCirugiaResponse> crear(@Valid @RequestBody SolicitudCirugiaRequest datos) {
        SolicitudCirugiaResponse creada = servicio.crear(datos);
        return ResponseEntity.created(URI.create("/api/solicitudes-cirugia/" + creada.id())).body(creada);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SOLICITUDES_CIRUGIA_CREAR')")
    @Operation(summary = "Edita los datos clínicos (solo en BORRADOR o ENVIADA)")
    public SolicitudCirugiaResponse actualizar(@PathVariable UUID id, @Valid @RequestBody DatosClinicosRequest datos) {
        return servicio.actualizar(id, datos);
    }

    @PostMapping("/{id}/requerimientos")
    @PreAuthorize("hasAuthority('SOLICITUDES_CIRUGIA_CREAR')")
    @Operation(summary = "Agrega un rol clínico requerido")
    public ResponseEntity<RequerimientoResponse> agregarRequerimiento(@PathVariable UUID id,
            @Valid @RequestBody RequerimientoRequest datos) {
        RequerimientoResponse creado = servicio.agregarRequerimiento(id, datos);
        return ResponseEntity.created(URI.create("/api/solicitudes-cirugia/" + id + "/requerimientos/" + creado.id()))
                .body(creado);
    }

    @PutMapping("/{id}/requerimientos/{requerimientoId}")
    @PreAuthorize("hasAuthority('SOLICITUDES_CIRUGIA_CREAR')")
    @Operation(summary = "Actualiza un requerimiento (no por debajo de sus asignaciones vigentes)")
    public RequerimientoResponse actualizarRequerimiento(@PathVariable UUID id, @PathVariable UUID requerimientoId,
            @Valid @RequestBody RequerimientoRequest datos) {
        return servicio.actualizarRequerimiento(id, requerimientoId, datos);
    }

    @DeleteMapping("/{id}/requerimientos/{requerimientoId}")
    @PreAuthorize("hasAuthority('SOLICITUDES_CIRUGIA_CREAR')")
    @Operation(summary = "Elimina un requerimiento sin asignaciones")
    public ResponseEntity<Void> eliminarRequerimiento(@PathVariable UUID id, @PathVariable UUID requerimientoId) {
        servicio.eliminarRequerimiento(id, requerimientoId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/enviar")
    @PreAuthorize("hasAuthority('SOLICITUDES_CIRUGIA_CREAR')")
    @Operation(summary = "BORRADOR → ENVIADA (exige al menos un requerimiento)")
    public SolicitudCirugiaResponse enviar(@PathVariable UUID id) {
        return servicio.enviar(id);
    }

    @PostMapping("/{id}/revisar")
    @PreAuthorize("hasAuthority('CIRUGIAS_PROGRAMAR')")
    @Operation(summary = "ENVIADA → EN_REVISION")
    public SolicitudCirugiaResponse revisar(@PathVariable UUID id) {
        return servicio.cambiarEstado(id, EstadoSolicitudCirugia.EN_REVISION);
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasAuthority('CIRUGIAS_PROGRAMAR')")
    @Operation(summary = "ENVIADA o EN_REVISION → APROBADA (lista para programar)")
    public SolicitudCirugiaResponse aprobar(@PathVariable UUID id) {
        return servicio.cambiarEstado(id, EstadoSolicitudCirugia.APROBADA);
    }

    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasAuthority('CIRUGIAS_PROGRAMAR')")
    @Operation(summary = "ENVIADA o EN_REVISION → RECHAZADA")
    public SolicitudCirugiaResponse rechazar(@PathVariable UUID id) {
        return servicio.cambiarEstado(id, EstadoSolicitudCirugia.RECHAZADA);
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyAuthority('SOLICITUDES_CIRUGIA_CREAR', 'CIRUGIAS_PROGRAMAR')")
    @Operation(summary = "Cancela una solicitud que aún no está programada, rechazada ni cancelada")
    public SolicitudCirugiaResponse cancelar(@PathVariable UUID id) {
        return servicio.cambiarEstado(id, EstadoSolicitudCirugia.CANCELADA);
    }
}
