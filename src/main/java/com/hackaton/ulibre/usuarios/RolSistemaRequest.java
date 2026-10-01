package com.hackaton.ulibre.usuarios;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RolSistemaRequest(
        @NotBlank @Size(max = 60) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "solo letras, números, guion y guion bajo")
        String codigo,
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 250) String descripcion,
        Boolean activo) {
}
