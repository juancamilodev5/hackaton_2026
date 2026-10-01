package com.hackaton.ulibre.procedimientos;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Set o instrumento predeterminado. cantidad por defecto 1 y esRequerido por defecto true. */
public record PredeterminadoRequest(
        @Positive(message = "debe ser mayor que cero") Integer cantidad,
        Boolean esRequerido,
        @Size(max = 250) String notas) {
}
