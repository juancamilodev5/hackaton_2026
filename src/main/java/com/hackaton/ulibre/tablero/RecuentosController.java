package com.hackaton.ulibre.tablero;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/cirugias/{cirugiaId}/recuentos")
@Tag(name = "Recuentos")
public class RecuentosController {

    private final RecuentosService servicio;

    public RecuentosController(RecuentosService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TABLERO_VER')")
    @Operation(summary = "Recuentos con cantidad esperada, estado (PENDIENTE/CUADRA/DISCREPANCIA) y confirmaciones por etapa")
    public List<RecuentoVista> listar(@PathVariable UUID cirugiaId) {
        return servicio.listar(cirugiaId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TABLERO_OPERAR')")
    @Operation(summary = "Registra el conteo inicial (solo el operador del tablero)")
    public ResponseEntity<RecuentoVista> registrarInicial(@PathVariable UUID cirugiaId,
            @Valid @RequestBody RecuentoInicialRequest datos) {
        RecuentoVista creado = servicio.registrarInicial(cirugiaId, datos);
        return ResponseEntity.created(URI.create("/api/cirugias/" + cirugiaId + "/recuentos/" + creado.id()))
                .body(creado);
    }

    @PostMapping("/{recuentoId}/agregados")
    @PreAuthorize("hasAuthority('TABLERO_OPERAR')")
    @Operation(summary = "Suma material abierto durante la cirugía (antes del conteo final)")
    public RecuentoVista agregarMaterial(@PathVariable UUID cirugiaId, @PathVariable UUID recuentoId,
            @Valid @RequestBody MaterialAgregadoRequest datos) {
        return servicio.agregarMaterial(cirugiaId, recuentoId, datos);
    }

    @PutMapping("/{recuentoId}/final")
    @PreAuthorize("hasAuthority('TABLERO_OPERAR')")
    @Operation(summary = "Registra el conteo final (se puede corregir mientras nadie lo haya confirmado)")
    public RecuentoVista registrarFinal(@PathVariable UUID cirugiaId, @PathVariable UUID recuentoId,
            @Valid @RequestBody RecuentoFinalRequest datos) {
        return servicio.registrarFinal(cirugiaId, recuentoId, datos);
    }

    /** TABLERO_VER: confirma quien contó, aunque no opere el tablero (ver EjecucionChecklistController). */
    @PostMapping("/{recuentoId}/confirmaciones")
    @PreAuthorize("hasAuthority('TABLERO_VER')")
    @Operation(summary = "Confirmación clínica de una etapa (INICIAL o FINAL) por una persona asignada a la cirugía")
    public RecuentoVista confirmar(@PathVariable UUID cirugiaId, @PathVariable UUID recuentoId,
            @Valid @RequestBody ConfirmacionRecuentoRequest datos) {
        return servicio.confirmar(cirugiaId, recuentoId, datos);
    }
}
