package com.hackaton.ulibre.cirugias;

import java.time.LocalDateTime;
import java.util.UUID;

import com.hackaton.ulibre.catalogos.RolClinicoVista;

public record AsignacionResponse(
        UUID id,
        UUID requerimientoRolId,
        RolClinicoVista rol,
        UUID profesionalId,
        String profesional,
        EstadoAsignacion estado,
        boolean esOperadorTablero,
        String asignadoPor,
        LocalDateTime asignadoEn,
        LocalDateTime confirmadoEn,
        String notas) {
}
