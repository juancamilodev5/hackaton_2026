package com.hackaton.ulibre.protocolos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FasePlantillaRequest(
        @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "solo letras, números, guion y guion bajo")
        String codigo,
        @NotBlank @Size(max = 150) String nombre,
        String descripcion,
        @NotNull @Min(value = 1, message = "debe ser mayor que cero") Integer orden) {
}
