package com.hackaton.ulibre.alertas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Lo único configurable de una regla. activo null = sin cambio. */
public record ReglaSeguridadRequest(
        @NotBlank @Size(max = 180) String nombre,
        @Size(max = 4000) String descripcion,
        @NotNull SeveridadAlerta severidad,
        @NotNull Boolean bloqueante,
        Boolean activo) {
}
