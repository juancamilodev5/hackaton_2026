package com.hackaton.ulibre.profesionales;

import java.util.UUID;

/**
 * Profesional libre en el rango consultado. deTurno / marcadoDisponible indican si además tiene un
 * bloque DE_TURNO o DISPONIBLE que se solapa con el rango (información, no requisito).
 */
public record ProfesionalDisponibleResponse(
        UUID profesionalId,
        UUID usuarioId,
        String nombres,
        String apellidos,
        boolean deTurno,
        boolean marcadoDisponible) {
}
