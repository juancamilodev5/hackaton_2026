package com.hackaton.ulibre.cirugias;

import java.util.UUID;

public record AsignacionVista(
        UUID asignacionId,
        UUID profesionalId,
        String profesional,
        EstadoAsignacion estado,
        boolean esOperadorTablero) {
}
