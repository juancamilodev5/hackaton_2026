package com.hackaton.ulibre.usuarios;

import java.util.UUID;

public record PermisoResponse(UUID id, String codigo, String nombre, String descripcion) {
}
