package com.hackaton.ulibre.solicitudes;

import java.util.UUID;

import com.hackaton.ulibre.solicitudes.SolicitudCirugiaResponse.Referencia;

/** asignacionesVigentes: personas ASIGNADA/CONFIRMADA que hoy cubren el requerimiento. */
public record RequerimientoResponse(
        UUID id,
        Referencia rolClinico,
        Referencia especialidad,
        int cantidad,
        boolean esRequerido,
        String notas,
        int asignacionesVigentes) {
}
