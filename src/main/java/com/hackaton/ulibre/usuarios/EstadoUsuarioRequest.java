package com.hackaton.ulibre.usuarios;

import com.hackaton.ulibre.auth.EstadoUsuario;
import jakarta.validation.constraints.NotNull;

public record EstadoUsuarioRequest(@NotNull EstadoUsuario estado) {
}
