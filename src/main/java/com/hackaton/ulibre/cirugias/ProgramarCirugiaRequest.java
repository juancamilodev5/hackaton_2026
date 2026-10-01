package com.hackaton.ulibre.cirugias;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Si finProgramado falta se calcula con la duración estimada del procedimiento. */
public record ProgramarCirugiaRequest(
        @NotNull UUID solicitudCirugiaId,
        @NotNull UUID quirofanoId,
        @NotNull LocalDateTime inicioProgramado,
        LocalDateTime finProgramado,
        UUID coordinadorUsuarioId,
        @Size(max = 2000) String notasProgramacion) {
}
