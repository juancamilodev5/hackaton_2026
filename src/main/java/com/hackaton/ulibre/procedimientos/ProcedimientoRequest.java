package com.hackaton.ulibre.procedimientos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Alta y edición de un procedimiento. El código se guarda en mayúsculas. */
public record ProcedimientoRequest(
        @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "solo letras, números, guion y guion bajo")
        String codigo,
        @NotBlank @Size(max = 180) String nombre,
        @Size(max = 4000) String descripcion,
        @Positive(message = "debe ser mayor que cero") Integer duracionEstimadaMinutos,
        Boolean activo) {
}
