package com.hackaton.ulibre.portal;

import java.time.LocalDateTime;
import java.util.UUID;

import com.hackaton.ulibre.citas.EstadoCita;

public record MiCitaResponse(
        UUID id,
        LocalDateTime programadaPara,
        String medico,
        String especialidad,
        EstadoCita estado,
        String motivo) {
}
