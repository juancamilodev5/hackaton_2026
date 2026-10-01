package com.hackaton.ulibre.pacientes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** severidad es texto libre (LEVE, MODERADA, SEVERA...); se guarda en mayúsculas. */
public record AlergiaRequest(
        @NotBlank @Size(max = 150) String sustancia,
        @Size(max = 250) String reaccion,
        @Size(max = 50) String severidad,
        String notas,
        Boolean activo) {
}
