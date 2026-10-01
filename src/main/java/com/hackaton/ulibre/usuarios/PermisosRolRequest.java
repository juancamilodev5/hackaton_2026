package com.hackaton.ulibre.usuarios;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Conjunto completo de permisos (por código) que debe quedar en el rol. */
public record PermisosRolRequest(@NotNull List<@NotBlank String> permisos) {
}
