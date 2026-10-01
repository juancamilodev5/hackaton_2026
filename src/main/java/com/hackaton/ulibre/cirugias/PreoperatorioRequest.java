package com.hackaton.ulibre.cirugias;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * Si estadoReservaSangre falta: PENDIENTE cuando se requiere reserva, NO_REQUERIDA si no.
 * Valores positivos y coherencia de la reserva los exigen los CHECK de la tabla.
 */
public record PreoperatorioRequest(
        @Digits(integer = 4, fraction = 2) BigDecimal pesoKg,
        @Digits(integer = 4, fraction = 2) BigDecimal tallaCm,
        @Digits(integer = 5, fraction = 2) BigDecimal glucometriaMgDl,
        @NotNull Boolean requiereReservaSangre,
        EstadoReservaSangre estadoReservaSangre,
        @Size(max = 4000) String informacionClinicaRelevante,
        JsonNode datosAdicionales) {
}
