package com.hackaton.ulibre.instrumental;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** cantidadPreparada = 0 deshace la preparación (quién y cuándo vuelven a null). */
public record PreparacionInstrumentalRequest(
        @NotNull @Min(value = 0, message = "no puede ser negativa") Integer cantidadPreparada,
        @Size(max = 2000) String notas) {
}
