package com.hackaton.ulibre.profesionales;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Horas locales de Colombia. Que termine después de iniciar lo valida la base (422). */
public record DisponibilidadRequest(
        @NotNull TipoDisponibilidad tipoDisponibilidad,
        @NotNull LocalDateTime iniciaEn,
        @NotNull LocalDateTime terminaEn,
        @Size(max = 250) String notas) {
}
