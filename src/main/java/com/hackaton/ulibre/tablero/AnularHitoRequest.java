package com.hackaton.ulibre.tablero;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnularHitoRequest(@NotBlank @Size(max = 250) String motivo) {
}
