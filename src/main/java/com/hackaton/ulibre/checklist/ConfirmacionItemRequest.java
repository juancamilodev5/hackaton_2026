package com.hackaton.ulibre.checklist;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** asignacionId: quién confirma (persona + rol en la cirugía); si falta, el usuario autenticado. */
public record ConfirmacionItemRequest(
        UUID asignacionId,
        @NotNull ResultadoConfirmacion resultado,
        @Size(max = 250) String notas) {
}
