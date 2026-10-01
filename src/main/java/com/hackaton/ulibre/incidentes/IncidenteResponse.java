package com.hackaton.ulibre.incidentes;

import java.time.LocalDateTime;
import java.util.UUID;

import com.hackaton.ulibre.alertas.SeveridadAlerta;

public record IncidenteResponse(
        UUID id,
        UUID cirugiaId,
        String categoria,
        SeveridadAlerta severidad,
        String descripcion,
        EstadoIncidente estado,
        UUID reportadoPorUsuarioId,
        LocalDateTime reportadoEn,
        UUID resueltoPorUsuarioId,
        LocalDateTime resueltoEn,
        String notasResolucion,
        LocalDateTime actualizadoEn) {

    static IncidenteResponse de(Incidente i) {
        return new IncidenteResponse(i.getId(), i.getCirugiaId(), i.getCategoria(), i.getSeveridad(),
                i.getDescripcion(), i.getEstado(), i.getReportadoPorUsuarioId(), i.getReportadoEn(),
                i.getResueltoPorUsuarioId(), i.getResueltoEn(), i.getNotasResolucion(), i.getActualizadoEn());
    }
}
