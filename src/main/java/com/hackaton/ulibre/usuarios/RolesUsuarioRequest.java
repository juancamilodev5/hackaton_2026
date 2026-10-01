package com.hackaton.ulibre.usuarios;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Conjunto completo de roles del sistema (por código) que debe quedar asignado. */
public record RolesUsuarioRequest(@NotNull List<@NotBlank String> roles) {
}
