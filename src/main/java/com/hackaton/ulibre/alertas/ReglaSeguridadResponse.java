package com.hackaton.ulibre.alertas;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record ReglaSeguridadResponse(
        UUID id,
        String codigo,
        String nombre,
        String descripcion,
        SeveridadAlerta severidad,
        boolean bloqueante,
        @JsonRawValue String condicion,
        boolean activo,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn) {

    static ReglaSeguridadResponse de(ReglaSeguridad r) {
        return new ReglaSeguridadResponse(r.getId(), r.getCodigo(), r.getNombre(), r.getDescripcion(),
                r.getSeveridad(), r.isBloqueante(), r.getCondicion(), r.isActivo(), r.getCreadoEn(),
                r.getActualizadoEn());
    }
}
