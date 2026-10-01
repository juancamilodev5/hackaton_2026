package com.hackaton.ulibre.cirugias;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** El motivo es obligatorio al cancelar o suspender. */
public record CambioEstadoCirugiaRequest(
        @NotNull EstadoCirugia estado,
        @Size(max = 500) String motivo) {
}
