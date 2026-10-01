package com.hackaton.ulibre.tablero;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** asignacionId: quien confirma clínicamente; si falta, la asignación vigente del usuario autenticado. */
public record ConfirmacionRecuentoRequest(
        @NotNull EtapaRecuento etapa,
        UUID asignacionId,
        @Size(max = 250) String notas) {
}
