package com.hackaton.ulibre.protocolos;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.hackaton.ulibre.catalogos.RolClinicoVista;

public record ItemPlantillaResponse(
        UUID id,
        UUID faseId,
        String codigo,
        String etiqueta,
        String descripcion,
        String tipoRespuesta,
        boolean obligatorio,
        boolean bloqueante,
        UUID rolClinicoResponsableId,
        /** Código y nombre del rol responsable; null si el ítem no exige rol. */
        RolClinicoVista rolClinicoResponsable,
        @JsonRawValue String configValidacion,
        int orden,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn) {

    static ItemPlantillaResponse de(ItemPlantillaChecklist i, RolClinicoVista rol) {
        return new ItemPlantillaResponse(i.getId(), i.getFaseId(), i.getCodigo(), i.getEtiqueta(), i.getDescripcion(),
                i.getTipoRespuesta(), i.isObligatorio(), i.isBloqueante(), i.getRolClinicoResponsableId(), rol,
                i.getConfigValidacion(), i.getOrden(), i.getCreadoEn(), i.getActualizadoEn());
    }
}
