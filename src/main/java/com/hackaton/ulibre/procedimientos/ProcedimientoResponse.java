package com.hackaton.ulibre.procedimientos;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProcedimientoResponse(
        UUID id,
        String codigo,
        String nombre,
        String descripcion,
        Integer duracionEstimadaMinutos,
        boolean activo,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn) {

    static ProcedimientoResponse de(ProcedimientoQuirurgico p) {
        return new ProcedimientoResponse(p.getId(), p.getCodigo(), p.getNombre(), p.getDescripcion(),
                p.getDuracionEstimadaMinutos(), p.isActivo(), p.getCreadoEn(), p.getActualizadoEn());
    }
}
