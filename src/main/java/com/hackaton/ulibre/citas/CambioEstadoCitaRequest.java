package com.hackaton.ulibre.citas;

import jakarta.validation.constraints.NotNull;

public record CambioEstadoCitaRequest(@NotNull EstadoCita estado) {
}
