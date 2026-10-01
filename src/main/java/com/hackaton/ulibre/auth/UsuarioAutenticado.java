package com.hackaton.ulibre.auth;

import java.util.List;
import java.util.UUID;

public record UsuarioAutenticado(
        UUID id,
        String nombres,
        String apellidos,
        String correo,
        List<String> roles,
        List<String> permisos) {

    static UsuarioAutenticado de(Usuario usuario, Acceso acceso) {
        return new UsuarioAutenticado(usuario.getId(), usuario.getNombres(), usuario.getApellidos(),
                usuario.getCorreo(), acceso.roles(), acceso.permisos());
    }
}
