package com.hackaton.ulibre.cirugias;

import jakarta.validation.constraints.NotNull;

/** CONFIRMADA, RECHAZADA o CANCELADA. */
public record CambioEstadoAsignacionRequest(@NotNull EstadoAsignacion estado) {
}
