package com.hackaton.ulibre.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = 180) String correo,
        @NotBlank @Size(max = 200) String contrasena) {
}
