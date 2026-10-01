package com.hackaton.ulibre.alertas;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cirugias/{cirugiaId}")
@Tag(name = "Alertas")
public class AlertasController {

    private final AlertasService servicio;

    public AlertasController(AlertasService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/alertas")
    @PreAuthorize("hasAuthority('TABLERO_VER')")
    @Operation(summary = "Alertas de la cirugía: vigentes primero, luego por severidad y fecha")
    public List<AlertaVista> listar(@PathVariable UUID cirugiaId) {
        return servicio.listar(cirugiaId);
    }

    @PostMapping("/reglas/evaluar")
    @PreAuthorize("hasAnyAuthority('TABLERO_OPERAR', 'ALERTAS_GESTIONAR')")
    @Operation(summary = "Reevalúa las reglas de seguridad: genera alertas nuevas y resuelve las que ya no aplican")
    public EvaluacionReglasResponse evaluar(@PathVariable UUID cirugiaId) {
        return servicio.evaluar(cirugiaId);
    }

    @PostMapping("/alertas/{alertaId}/reconocer")
    @PreAuthorize("hasAuthority('ALERTAS_GESTIONAR')")
    @Operation(summary = "Reconoce una alerta ABIERTA (quién y cuándo); sigue vigente")
    public AlertaVista reconocer(@PathVariable UUID cirugiaId, @PathVariable UUID alertaId) {
        return servicio.reconocer(cirugiaId, alertaId);
    }

    @PostMapping("/alertas/{alertaId}/resolver")
    @PreAuthorize("hasAuthority('ALERTAS_GESTIONAR')")
    @Operation(summary = "Resuelve una alerta vigente con notas; 422 si la condición de su regla sigue presente")
    public AlertaVista resolver(@PathVariable UUID cirugiaId, @PathVariable UUID alertaId,
            @Valid @RequestBody NotasAlertaRequest datos) {
        return servicio.resolver(cirugiaId, alertaId, datos);
    }

    @PostMapping("/alertas/{alertaId}/descartar")
    @PreAuthorize("hasAuthority('ALERTAS_GESTIONAR')")
    @Operation(summary = "Descarta una alerta no bloqueante con notas")
    public AlertaVista descartar(@PathVariable UUID cirugiaId, @PathVariable UUID alertaId,
            @Valid @RequestBody NotasAlertaRequest datos) {
        return servicio.descartar(cirugiaId, alertaId, datos);
    }

    @PostMapping("/alertas/{alertaId}/excepcion")
    @PreAuthorize("hasAuthority('ALERTAS_GESTIONAR')")
    @Operation(summary = "Autoriza continuar frente a una alerta bloqueante (quién, cuándo y motivo); la alerta sigue vigente")
    public AlertaVista excepcion(@PathVariable UUID cirugiaId, @PathVariable UUID alertaId,
            @Valid @RequestBody ExcepcionAlertaRequest datos) {
        return servicio.autorizarExcepcion(cirugiaId, alertaId, datos);
    }
}
