package com.hackaton.ulibre.auth;

import java.util.List;

/** Roles del sistema activos de un usuario y la unión de sus permisos. */
public record Acceso(List<String> roles, List<String> permisos) {
}
