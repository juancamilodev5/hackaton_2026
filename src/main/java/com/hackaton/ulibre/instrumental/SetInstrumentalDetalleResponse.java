package com.hackaton.ulibre.instrumental;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.hackaton.ulibre.comun.catalogo.CatalogoResponse;

/** Set del catálogo con su composición (instrumentos y cantidades). */
public record SetInstrumentalDetalleResponse(
        UUID id,
        String codigo,
        String nombre,
        String descripcion,
        boolean activo,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn,
        List<InstrumentoSetResponse> instrumentos) {

    static SetInstrumentalDetalleResponse de(CatalogoResponse set, List<InstrumentoSetResponse> instrumentos) {
        return new SetInstrumentalDetalleResponse(set.id(), set.codigo(), set.nombre(), set.descripcion(),
                set.activo(), set.creadoEn(), set.actualizadoEn(), instrumentos);
    }
}
