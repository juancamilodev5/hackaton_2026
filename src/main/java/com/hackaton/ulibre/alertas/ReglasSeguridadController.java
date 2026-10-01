package com.hackaton.ulibre.alertas;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reglas-seguridad")
@Tag(name = "Reglas de seguridad")
public class ReglasSeguridadController {

    private final ReglaSeguridadService servicio;

    public ReglasSeguridadController(ReglaSeguridadService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CATALOGOS_VER')")
    @Operation(summary = "Reglas de seguridad, ordenadas por código (filtro opcional por activo)")
    public List<ReglaSeguridadResponse> listar(@RequestParam(required = false) Boolean activo) {
        return servicio.listar(activo);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOGOS_VER')")
    @Operation(summary = "Regla de seguridad por id")
    public ReglaSeguridadResponse obtener(@PathVariable UUID id) {
        return servicio.obtener(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOGOS_GESTIONAR')")
    @Operation(summary = "Configura una regla: nombre, descripción, severidad, bloqueante y activo (el código no cambia)")
    public ReglaSeguridadResponse actualizar(@PathVariable UUID id, @Valid @RequestBody ReglaSeguridadRequest datos) {
        return servicio.actualizar(id, datos);
    }
}
