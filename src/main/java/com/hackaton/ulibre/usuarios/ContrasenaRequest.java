package com.hackaton.ulibre.usuarios;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContrasenaRequest(
        @NotBlank @Size(min = 8, max = 200, message = "debe tener entre 8 y 200 caracteres") String contrasena) {
}
