package com.hackaton.ulibre.profesionales;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Habilita a un usuario existente como profesional; roles clínicos y especialidades se asignan aparte. */
public record NuevoProfesionalRequest(
        @NotNull UUID usuarioId,
        @Size(max = 100) String licenciaProfesional,
        @Size(max = 100) String numeroProfesional) {
}
