package com.hackaton.ulibre.pacientes;

import java.time.LocalDateTime;
import java.util.UUID;

public record AlergiaResponse(
        UUID id,
        String sustancia,
        String reaccion,
        String severidad,
        String notas,
        boolean activo,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn) {

    static AlergiaResponse de(AlergiaPaciente a) {
        return new AlergiaResponse(a.getId(), a.getSustancia(), a.getReaccion(), a.getSeveridad(), a.getNotas(),
                a.isActivo(), a.getCreadoEn(), a.getActualizadoEn());
    }
}
