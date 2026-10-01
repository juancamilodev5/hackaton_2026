package com.hackaton.ulibre.cirugias;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReprogramarCirugiaRequest(
        @NotNull UUID quirofanoId,
        @NotNull LocalDateTime inicioProgramado,
        @NotNull LocalDateTime finProgramado,
        UUID coordinadorUsuarioId,
        @Size(max = 2000) String notasProgramacion) {
}
