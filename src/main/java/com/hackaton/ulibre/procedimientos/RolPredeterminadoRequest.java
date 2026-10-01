package com.hackaton.ulibre.procedimientos;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** cantidadPredeterminada por defecto 1 y esRequerido por defecto true. */
public record RolPredeterminadoRequest(
        @NotNull UUID rolClinicoId,
        UUID especialidadId,
        @Positive(message = "debe ser mayor que cero") Integer cantidadPredeterminada,
        Boolean esRequerido,
        @Size(max = 250) String notas) {
}
