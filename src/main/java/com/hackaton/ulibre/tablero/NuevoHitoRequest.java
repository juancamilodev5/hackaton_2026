package com.hackaton.ulibre.tablero;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * ocurridoEn: por defecto ahora (hora de Colombia); no puede ser futuro.
 * corrigeHitoId: hito anulado que este reemplaza (corrección de un registro equivocado).
 */
public record NuevoHitoRequest(
        @NotNull TipoHito tipoHito,
        LocalDateTime ocurridoEn,
        @Size(max = 250) String notas,
        UUID corrigeHitoId) {
}
