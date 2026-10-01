package com.hackaton.ulibre.checklist;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/cirugias/{cirugiaId}/checklist")
@Tag(name = "Checklist quirúrgico")
public class EjecucionChecklistController {

    private final EjecucionChecklistService servicio;

    public EjecucionChecklistController(EjecucionChecklistService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TABLERO_VER')")
    @Operation(summary = "Checklist de la cirugía: fases → ítems → confirmaciones (404 si no se ha iniciado)")
    public ChecklistVista obtener(@PathVariable UUID cirugiaId) {
        return servicio.obtener(cirugiaId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TABLERO_OPERAR')")
    @Operation(summary = "Inicia el checklist (copia la plantilla con fn_iniciar_checklist si hace falta); solo el operador")
    public ResponseEntity<ChecklistVista> iniciar(@PathVariable UUID cirugiaId,
            @Valid @RequestBody(required = false) IniciarChecklistRequest datos) {
        ChecklistVista checklist = servicio.iniciar(cirugiaId, datos == null ? null : datos.plantillaId());
        return ResponseEntity.status(HttpStatus.CREATED).body(checklist);
    }

    @PutMapping("/items/{itemId}/respuesta")
    @PreAuthorize("hasAuthority('TABLERO_OPERAR')")
    @Operation(summary = "Registra la respuesta de un ítem de la fase actual (forma según tipo_respuesta); solo el operador")
    public ChecklistVista responder(@PathVariable UUID cirugiaId, @PathVariable UUID itemId,
            @Valid @RequestBody RespuestaItemRequest datos) {
        return servicio.responder(cirugiaId, itemId, datos);
    }

    /**
     * TABLERO_VER y no TABLERO_OPERAR: quien confirma clínicamente (p. ej. la anestesióloga, rol MEDICO)
     * no opera el tablero. Que participe en la cirugía y que solo el operador registre por otro lo
     * exige el servicio; el rol clínico del ítem, la base.
     */
    @PostMapping("/items/{itemId}/confirmaciones")
    @PreAuthorize("hasAuthority('TABLERO_VER')")
    @Operation(summary = "Confirmación clínica de un ítem por quien cubre su rol (la registra el operador o la propia persona)")
    public ResponseEntity<ChecklistVista> confirmar(@PathVariable UUID cirugiaId, @PathVariable UUID itemId,
            @Valid @RequestBody ConfirmacionItemRequest datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.confirmar(cirugiaId, itemId, datos));
    }

    @PostMapping("/fases/{faseId}/cerrar")
    @PreAuthorize("hasAuthority('TABLERO_OPERAR')")
    @Operation(summary = "Cierra la fase actual (evalúa reglas; la base rechaza si hay alertas bloqueantes); solo el operador")
    public ChecklistVista cerrarFase(@PathVariable UUID cirugiaId, @PathVariable UUID faseId,
            @Valid @RequestBody(required = false) CierreFaseRequest datos) {
        return servicio.cerrarFase(cirugiaId, faseId, datos == null ? null : datos.notas());
    }
}
