package com.hackaton.ulibre.comun;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Id del usuario autenticado (claim {@code sub} del JWT), para las columnas "quién lo hizo". */
public final class UsuarioActual {

    private UsuarioActual() {
    }

    public static UUID id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No hay un usuario autenticado en el contexto");
        }
        return UUID.fromString(jwt.getSubject());
    }
}
