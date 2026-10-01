package com.hackaton.ulibre.profesionales;

import java.time.LocalDateTime;
import java.util.UUID;

public record DisponibilidadResponse(
        UUID id,
        UUID profesionalId,
        TipoDisponibilidad tipoDisponibilidad,
        LocalDateTime iniciaEn,
        LocalDateTime terminaEn,
        String notas,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn) {

    static DisponibilidadResponse de(DisponibilidadProfesional d) {
        return new DisponibilidadResponse(d.getId(), d.getProfesionalId(), d.getTipoDisponibilidad(), d.getIniciaEn(),
                d.getTerminaEn(), d.getNotas(), d.getCreadoEn(), d.getActualizadoEn());
    }
}
