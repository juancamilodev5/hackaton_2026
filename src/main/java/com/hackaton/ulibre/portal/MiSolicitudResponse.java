package com.hackaton.ulibre.portal;

import java.time.LocalDateTime;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.Lateralidad;
import com.hackaton.ulibre.solicitudes.EstadoSolicitudCirugia;

/** Sin notas médicas: son internas del equipo. cirugiaId si ya se programó. */
public record MiSolicitudResponse(
        UUID id,
        String procedimiento,
        String especialidad,
        String medico,
        String sitioQuirurgico,
        Lateralidad lateralidad,
        EstadoSolicitudCirugia estado,
        LocalDateTime creadaEn,
        LocalDateTime enviadaEn,
        UUID cirugiaId) {
}
