package com.hackaton.ulibre.instrumental;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cirugias/{cirugiaId}/instrumental")
@Tag(name = "Instrumental de la cirugía")
public class InstrumentalCirugiaController {

    private final InstrumentalCirugiaService servicio;

    public InstrumentalCirugiaController(InstrumentalCirugiaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CIRUGIAS_VER', 'TABLERO_VER')")
    @Operation(summary = "Instrumental de la cirugía: sets, instrumentos directos, instrumentalCompleto y faltantes")
    public InstrumentalVista obtener(@PathVariable UUID cirugiaId) {
        return servicio.obtener(cirugiaId);
    }

    @PostMapping("/preparar")
    @PreAuthorize("hasAuthority('PREPARACION_QUIRURGICA_GESTIONAR')")
    @Operation(summary = "Copia el instrumental predeterminado del procedimiento a la cirugía (fn_preparar_instrumental)")
    public InstrumentalVista preparar(@PathVariable UUID cirugiaId) {
        return servicio.preparar(cirugiaId);
    }

    @PutMapping("/sets/{setCirugiaId}")
    @PreAuthorize("hasAuthority('PREPARACION_QUIRURGICA_GESTIONAR')")
    @Operation(summary = "Registra la cantidad preparada de un set (quien prepara debe estar asignado a la cirugía)")
    public InstrumentalVista prepararSet(@PathVariable UUID cirugiaId, @PathVariable UUID setCirugiaId,
            @Valid @RequestBody PreparacionInstrumentalRequest datos) {
        return servicio.prepararSet(cirugiaId, setCirugiaId, datos);
    }

    @PutMapping("/instrumentos/{instrumentoCirugiaId}")
    @PreAuthorize("hasAuthority('PREPARACION_QUIRURGICA_GESTIONAR')")
    @Operation(summary = "Registra la cantidad preparada de un instrumento (quien prepara debe estar asignado a la cirugía)")
    public InstrumentalVista prepararInstrumento(@PathVariable UUID cirugiaId, @PathVariable UUID instrumentoCirugiaId,
            @Valid @RequestBody PreparacionInstrumentalRequest datos) {
        return servicio.prepararInstrumento(cirugiaId, instrumentoCirugiaId, datos);
    }
}
