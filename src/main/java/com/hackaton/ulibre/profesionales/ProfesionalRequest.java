package com.hackaton.ulibre.profesionales;

import jakarta.validation.constraints.Size;

/** Edición del perfil; el usuario vinculado no cambia. */
public record ProfesionalRequest(
        @Size(max = 100) String licenciaProfesional,
        @Size(max = 100) String numeroProfesional,
        Boolean activo) {
}
