package com.hackaton.ulibre.portal;

import java.time.LocalDateTime;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.EstadoCirugia;
import com.hackaton.ulibre.cirugias.Lateralidad;

/** Programación de una cirugía propia; no incluye equipo, checklist ni alertas. */
public record MiCirugiaResponse(
        UUID id,
        UUID solicitudId,
        String procedimiento,
        String sitioQuirurgico,
        Lateralidad lateralidad,
        String quirofano,
        LocalDateTime inicioProgramado,
        LocalDateTime finProgramado,
        EstadoCirugia estado) {
}
