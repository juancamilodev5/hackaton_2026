package com.hackaton.ulibre.incidentes;

import com.hackaton.ulibre.alertas.SeveridadAlerta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IncidenteRequest(
        @Size(max = 100) String categoria,
        SeveridadAlerta severidad,
        @NotBlank @Size(max = 4000) String descripcion) {
}
