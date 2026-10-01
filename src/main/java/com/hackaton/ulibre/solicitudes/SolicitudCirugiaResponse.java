package com.hackaton.ulibre.solicitudes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.Lateralidad;

/** Detalle de una solicitud con sus requerimientos; cirugiaId es null mientras no esté programada. */
public record SolicitudCirugiaResponse(
        UUID id,
        EstadoSolicitudCirugia estado,
        Paciente paciente,
        Medico medico,
        Referencia especialidad,
        Referencia procedimiento,
        UUID citaOrigenId,
        String sitioQuirurgico,
        Lateralidad lateralidad,
        String resumenClinico,
        String notasMedicas,
        LocalDateTime creadoEn,
        LocalDateTime enviadaEn,
        LocalDateTime actualizadoEn,
        UUID cirugiaId,
        List<RequerimientoResponse> requerimientos) {

    public record Paciente(UUID id, String nombre, String tipoDocumento, String numeroDocumento) {
    }

    public record Medico(UUID id, String nombre) {
    }

    public record Referencia(UUID id, String codigo, String nombre) {
    }
}
