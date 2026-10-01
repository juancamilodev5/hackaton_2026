package com.hackaton.ulibre.cirugias;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AsignacionRequest(
        @NotNull UUID requerimientoRolId,
        @NotNull UUID profesionalId,
        Boolean esOperadorTablero,
        @Size(max = 250) String notas) {
}
