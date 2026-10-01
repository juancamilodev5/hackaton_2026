package com.hackaton.ulibre.instrumental;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CantidadInstrumentoRequest(
        @NotNull @Min(value = 1, message = "debe ser mayor que cero") Integer cantidad) {
}
