package com.hackaton.ulibre.tablero;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** instrumentoCirugiaId: opcional, liga el recuento a un instrumento del snapshot de ESTA cirugía. */
public record RecuentoInicialRequest(
        @NotNull TipoRecuento tipoRecuento,
        @Size(max = 150) String descripcion,
        UUID instrumentoCirugiaId,
        @NotNull @Min(value = 0, message = "no puede ser negativa") Integer cantidadInicial,
        @Size(max = 2000) String notas) {
}
