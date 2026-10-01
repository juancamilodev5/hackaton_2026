package com.hackaton.ulibre.cirugias;

import java.time.LocalDateTime;
import java.util.UUID;

import com.hackaton.ulibre.catalogos.QuirofanoVista;

public record ProgramacionCirugiaResponse(
        UUID id,
        UUID solicitudCirugiaId,
        EstadoCirugia estado,
        Persona paciente,
        ProcedimientoVista procedimiento,
        QuirofanoVista quirofano,
        LocalDateTime inicioProgramado,
        LocalDateTime finProgramado,
        Persona programadaPor,
        Persona coordinador,
        String notasProgramacion,
        boolean equipoCompleto,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn) {

    public record Persona(UUID id, String nombre) {
    }
}
