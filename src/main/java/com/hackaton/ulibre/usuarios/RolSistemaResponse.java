package com.hackaton.ulibre.usuarios;

import java.util.List;
import java.util.UUID;

public record RolSistemaResponse(
        UUID id,
        String codigo,
        String nombre,
        String descripcion,
        boolean activo,
        List<String> permisos) {
}
