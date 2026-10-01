package com.hackaton.ulibre.alertas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Resolver o descartar exige quién, cuándo y por qué (ck_alerta_cierre). */
public record NotasAlertaRequest(@NotBlank @Size(max = 4000) String notas) {
}
