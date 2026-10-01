package com.hackaton.ulibre.comun.catalogo;

import java.time.LocalDateTime;
import java.util.UUID;

public record CatalogoResponse(
        UUID id,
        String codigo,
        String nombre,
        String descripcion,
        boolean activo,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn) {

    public static CatalogoResponse de(Catalogo c) {
        return new CatalogoResponse(c.getId(), c.getCodigo(), c.getNombre(), c.getDescripcion(), c.isActivo(),
                c.getCreadoEn(), c.getActualizadoEn());
    }
}
