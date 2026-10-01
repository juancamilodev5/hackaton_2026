package com.hackaton.ulibre.protocolos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta y edición de una plantilla. La versión no se envía: se asigna al crear (máxima del código + 1). */
public record PlantillaRequest(
        @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "solo letras, números, guion y guion bajo")
        String codigo,
        @NotBlank @Size(max = 180) String nombre,
        String descripcion) {
}
