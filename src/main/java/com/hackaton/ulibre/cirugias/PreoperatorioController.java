package com.hackaton.ulibre.cirugias;

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
@RequestMapping("/api/cirugias/{cirugiaId}/preoperatorio")
@Tag(name = "Preoperatorio")
public class PreoperatorioController {

    private final PreoperatorioService servicio;

    public PreoperatorioController(PreoperatorioService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CIRUGIAS_VER')")
    @Operation(summary = "Datos preoperatorios de la cirugía (404 si aún no se registran)")
    public PreoperatorioResponse obtener(@PathVariable UUID cirugiaId) {
        return servicio.obtener(cirugiaId);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('PREPARACION_QUIRURGICA_GESTIONAR')")
    @Operation(summary = "Crea o actualiza los datos preoperatorios (al crearlos copia las alergias activas; "
            + "cualquier cambio exige validar de nuevo)")
    public PreoperatorioResponse guardar(@PathVariable UUID cirugiaId,
            @Valid @RequestBody PreoperatorioRequest datos) {
        return servicio.guardar(cirugiaId, datos);
    }

    @PostMapping("/actualizar-alergias")
    @PreAuthorize("hasAuthority('PREPARACION_QUIRURGICA_GESTIONAR')")
    @Operation(summary = "Vuelve a copiar las alergias activas del paciente (exige validar de nuevo)")
    public PreoperatorioResponse actualizarAlergias(@PathVariable UUID cirugiaId) {
        return servicio.actualizarAlergias(cirugiaId);
    }

    @PostMapping("/validar")
    @PreAuthorize("hasAuthority('PREPARACION_QUIRURGICA_GESTIONAR')")
    @Operation(summary = "Marca los datos preoperatorios como validados por el usuario autenticado")
    public PreoperatorioResponse validar(@PathVariable UUID cirugiaId) {
        return servicio.validar(cirugiaId);
    }
}
