package com.hackaton.ulibre.protocolos;

import java.time.LocalDateTime;
import java.util.UUID;

/** Cabecera de una plantilla (sin fases), para listados. */
public record PlantillaResponse(
        UUID id,
        String codigo,
        String nombre,
        String descripcion,
        int version,
        EstadoPlantilla estado,
        LocalDateTime publicadaEn,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn) {

    static PlantillaResponse de(PlantillaChecklist p) {
        return new PlantillaResponse(p.getId(), p.getCodigo(), p.getNombre(), p.getDescripcion(), p.getVersion(),
                p.getEstado(), p.getPublicadaEn(), p.getCreadoEn(), p.getActualizadoEn());
    }
}
