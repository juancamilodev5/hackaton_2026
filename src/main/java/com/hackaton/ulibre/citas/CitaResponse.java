package com.hackaton.ulibre.citas;

import java.time.LocalDateTime;
import java.util.UUID;

public record CitaResponse(
        UUID id,
        Referencia paciente,
        Referencia medico,
        Especialidad especialidad,
        LocalDateTime programadaPara,
        String motivo,
        EstadoCita estado,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn) {

    /** Persona con su nombre completo (paciente o médico). */
    public record Referencia(UUID id, String nombre) {
    }

    public record Especialidad(UUID id, String codigo, String nombre) {
    }
}
