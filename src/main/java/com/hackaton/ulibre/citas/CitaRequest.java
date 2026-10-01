package com.hackaton.ulibre.citas;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** medicoId es el id del perfil profesional (no del usuario). Hora local de Colombia. */
public record CitaRequest(
        @NotNull UUID pacienteId,
        @NotNull UUID medicoId,
        @NotNull UUID especialidadId,
        @NotNull LocalDateTime programadaPara,
        String motivo) {
}
