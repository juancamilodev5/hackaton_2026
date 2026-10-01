package com.hackaton.ulibre.tablero;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Material abierto durante la cirugía: se suma a cantidad_agregada. */
public record MaterialAgregadoRequest(
        @NotNull @Min(value = 1, message = "debe ser mayor que cero") Integer cantidad,
        @Size(max = 2000) String notas) {
}
