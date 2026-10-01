package com.hackaton.ulibre.protocolos;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * Ítem de una fase. tipoRespuesta es texto libre en mayúsculas (CONFIRMACION, BOOLEANO, TEXTO,
 * NUMERO, SELECCION...): la base no lo restringe. obligatorio = true y bloqueante = false si se omiten.
 */
public record ItemPlantillaRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "solo letras, números, guion y guion bajo")
        String codigo,
        @NotBlank @Size(max = 250) String etiqueta,
        String descripcion,
        @NotBlank @Size(max = 50) String tipoRespuesta,
        Boolean obligatorio,
        Boolean bloqueante,
        UUID rolClinicoResponsableId,
        JsonNode configValidacion,
        @NotNull @Min(value = 1, message = "debe ser mayor que cero") Integer orden) {
}
