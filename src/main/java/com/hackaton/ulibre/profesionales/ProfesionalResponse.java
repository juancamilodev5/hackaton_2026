package com.hackaton.ulibre.profesionales;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProfesionalResponse(
        UUID id,
        UUID usuarioId,
        String nombres,
        String apellidos,
        String correo,
        String licenciaProfesional,
        String numeroProfesional,
        boolean activo,
        List<Referencia> rolesClinicos,
        List<Referencia> especialidades,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn) {
}
