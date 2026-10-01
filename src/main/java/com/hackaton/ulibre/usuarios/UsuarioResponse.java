package com.hackaton.ulibre.usuarios;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.hackaton.ulibre.auth.EstadoUsuario;

/** Nunca incluye el hash de la contraseña; solo si el usuario tiene una. */
public record UsuarioResponse(
        UUID id,
        String nombres,
        String apellidos,
        String correo,
        String telefono,
        EstadoUsuario estado,
        boolean tieneContrasena,
        List<String> roles,
        UUID perfilProfesionalId,
        UUID pacienteId,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn) {
}
