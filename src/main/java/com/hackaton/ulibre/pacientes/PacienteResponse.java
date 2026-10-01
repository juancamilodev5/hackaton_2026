package com.hackaton.ulibre.pacientes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Detalle del paciente con edad calculada y alergias activas. */
public record PacienteResponse(
        UUID id,
        UUID usuarioId,
        String nombres,
        String apellidos,
        String tipoDocumento,
        String numeroDocumento,
        LocalDate fechaNacimiento,
        Integer edad,
        String telefono,
        String correo,
        String contactoEmergenciaNombre,
        String contactoEmergenciaTelefono,
        List<AlergiaResponse> alergias,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn) {
}
