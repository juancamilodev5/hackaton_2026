package com.hackaton.ulibre.usuarios;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Edición de datos básicos; contraseña, estado y roles tienen sus propios endpoints. */
public record UsuarioRequest(
        @NotBlank @Size(max = 100) String nombres,
        @NotBlank @Size(max = 100) String apellidos,
        @NotBlank @Email @Size(max = 180) String correo,
        @Size(max = 50) String telefono) {
}
