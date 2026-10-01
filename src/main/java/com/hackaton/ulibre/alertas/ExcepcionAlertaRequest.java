package com.hackaton.ulibre.alertas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Regla 16: continuar frente a un bloqueo exige usuario, fecha y motivo. */
public record ExcepcionAlertaRequest(@NotBlank @Size(max = 4000) String motivo) {
}
