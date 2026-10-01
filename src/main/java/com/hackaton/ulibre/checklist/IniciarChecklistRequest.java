package com.hackaton.ulibre.checklist;

import java.util.UUID;

/** plantillaId opcional: si falta, fn_iniciar_checklist usa la predeterminada del procedimiento. */
public record IniciarChecklistRequest(UUID plantillaId) {
}
