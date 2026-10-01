package com.hackaton.ulibre.protocolos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Plantilla completa: fases ordenadas → ítems ordenados. */
public record PlantillaDetalleResponse(
        UUID id,
        String codigo,
        String nombre,
        String descripcion,
        int version,
        EstadoPlantilla estado,
        LocalDateTime publicadaEn,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn,
        List<FasePlantillaResponse> fases) {

    static PlantillaDetalleResponse de(PlantillaChecklist p, List<FasePlantillaResponse> fases) {
        return new PlantillaDetalleResponse(p.getId(), p.getCodigo(), p.getNombre(), p.getDescripcion(),
                p.getVersion(), p.getEstado(), p.getPublicadaEn(), p.getCreadoEn(), p.getActualizadoEn(), fases);
    }
}
