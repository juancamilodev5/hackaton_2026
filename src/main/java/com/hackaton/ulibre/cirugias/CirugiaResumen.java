package com.hackaton.ulibre.cirugias;

import java.time.LocalDateTime;
import java.util.UUID;

import com.hackaton.ulibre.catalogos.QuirofanoVista;

/** Fila del panel de cirugías del día. */
public record CirugiaResumen(
        UUID id,
        String paciente,
        ProcedimientoVista procedimiento,
        QuirofanoVista quirofano,
        LocalDateTime inicioProgramado,
        LocalDateTime finProgramado,
        EstadoCirugia estado,
        /** Bloqueantes ABIERTA/RECONOCIDA sin excepción autorizada: las que de verdad bloquean. */
        int alertasBloqueantesAbiertas,
        /** Nombre del operador del tablero vigente, o null. */
        String operadorTablero,
        boolean equipoCompleto) {
}
