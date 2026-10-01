package com.hackaton.ulibre.protocolos;

import java.util.List;
import java.util.UUID;

/** Fase con sus ítems ordenados. */
public record FasePlantillaResponse(
        UUID id,
        UUID plantillaId,
        String codigo,
        String nombre,
        String descripcion,
        int orden,
        List<ItemPlantillaResponse> items) {

    static FasePlantillaResponse de(FasePlantillaChecklist f, List<ItemPlantillaResponse> items) {
        return new FasePlantillaResponse(f.getId(), f.getPlantillaId(), f.getCodigo(), f.getNombre(),
                f.getDescripcion(), f.getOrden(), items);
    }
}
