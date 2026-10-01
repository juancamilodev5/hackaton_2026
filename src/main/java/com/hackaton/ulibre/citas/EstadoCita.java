package com.hackaton.ulibre.citas;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Enum de PostgreSQL {@code estado_cita}. La base no restringe las transiciones: se definen aquí.
 */
public enum EstadoCita {
    SOLICITADA, CONFIRMADA, COMPLETADA, CANCELADA, NO_ASISTIO;

    private static final Map<EstadoCita, Set<EstadoCita>> TRANSICIONES = new EnumMap<>(EstadoCita.class);

    static {
        TRANSICIONES.put(SOLICITADA, EnumSet.of(CONFIRMADA, CANCELADA));
        TRANSICIONES.put(CONFIRMADA, EnumSet.of(COMPLETADA, CANCELADA, NO_ASISTIO));
        TRANSICIONES.put(COMPLETADA, EnumSet.noneOf(EstadoCita.class));
        TRANSICIONES.put(CANCELADA, EnumSet.noneOf(EstadoCita.class));
        TRANSICIONES.put(NO_ASISTIO, EnumSet.noneOf(EstadoCita.class));
    }

    public boolean puedePasarA(EstadoCita destino) {
        return TRANSICIONES.get(this).contains(destino);
    }

    /** Solo una cita pendiente de atender se reprograma o edita. */
    public boolean admiteEdicion() {
        return this == SOLICITADA || this == CONFIRMADA;
    }
}
