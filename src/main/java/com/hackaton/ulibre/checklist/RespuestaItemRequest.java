package com.hackaton.ulibre.checklist;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * estado: COMPLETADO, ADVERTENCIA, FALLIDO o NO_APLICA. respuesta: JSON cuya forma depende del
 * tipo_respuesta del ítem (se guarda tal cual en jsonb); obligatoria salvo NO_APLICA.
 */
public record RespuestaItemRequest(
        @NotNull EstadoItemChecklist estado,
        JsonNode respuesta,
        @Size(max = 4000) String notas) {
}
