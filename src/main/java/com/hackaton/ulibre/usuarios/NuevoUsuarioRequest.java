package com.hackaton.ulibre.usuarios;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Alta de usuario. Sin contraseña el usuario existe pero no puede iniciar sesión. */
public record NuevoUsuarioRequest(
        @NotBlank @Size(max = 100) String nombres,
        @NotBlank @Size(max = 100) String apellidos,
        @NotBlank @Email @Size(max = 180) String correo,
        @Size(max = 50) String telefono,
        @Size(min = 8, max = 200, message = "debe tener entre 8 y 200 caracteres") String contrasena,
        List<@NotBlank String> rolesSistema) {
}
