package com.hackaton.ulibre.tablero;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecuentoFinalRequest(
        @NotNull @Min(value = 0, message = "no puede ser negativa") Integer cantidadFinal,
        @Size(max = 2000) String notas) {
}
