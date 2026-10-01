package com.hackaton.ulibre.solicitudes;

import java.time.LocalDateTime;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.Lateralidad;
import com.hackaton.ulibre.solicitudes.SolicitudCirugiaResponse.Medico;
import com.hackaton.ulibre.solicitudes.SolicitudCirugiaResponse.Paciente;
import com.hackaton.ulibre.solicitudes.SolicitudCirugiaResponse.Referencia;

/** Fila del listado de solicitudes. */
public record SolicitudCirugiaResumen(
        UUID id,
        EstadoSolicitudCirugia estado,
        Paciente paciente,
        Medico medico,
        Referencia especialidad,
        Referencia procedimiento,
        String sitioQuirurgico,
        Lateralidad lateralidad,
        LocalDateTime creadoEn,
        LocalDateTime enviadaEn,
        UUID cirugiaId) {
}
