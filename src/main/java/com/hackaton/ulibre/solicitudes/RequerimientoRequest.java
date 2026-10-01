package com.hackaton.ulibre.solicitudes;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Rol clínico que el médico pide para la solicitud. cantidad por defecto 1; esRequerido por defecto true. */
public record RequerimientoRequest(
        @NotNull UUID rolClinicoId,
        UUID especialidadId,
        @Positive Integer cantidad,
        Boolean esRequerido,
        @Size(max = 250) String notas) {
}
