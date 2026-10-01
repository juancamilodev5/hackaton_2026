package com.hackaton.ulibre.instrumental;

import java.util.UUID;

/** Un instrumento dentro de la composición de un set del catálogo. */
public record InstrumentoSetResponse(
        UUID instrumentoId,
        String codigo,
        String nombre,
        boolean activo,
        int cantidad) {
}
