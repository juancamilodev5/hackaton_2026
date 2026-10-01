package com.hackaton.ulibre.solicitudes;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Enum de PostgreSQL {@code estado_solicitud_cirugia}. La base no restringe las transiciones: el
 * único lugar donde se definen es {@link #TRANSICIONES}.
 */
public enum EstadoSolicitudCirugia {
    BORRADOR, ENVIADA, EN_REVISION, APROBADA, PROGRAMADA, RECHAZADA, CANCELADA;

    private static final Map<EstadoSolicitudCirugia, Set<EstadoSolicitudCirugia>> TRANSICIONES =
            new EnumMap<>(EstadoSolicitudCirugia.class);

    static {
        TRANSICIONES.put(BORRADOR, EnumSet.of(ENVIADA, CANCELADA));
        TRANSICIONES.put(ENVIADA, EnumSet.of(EN_REVISION, APROBADA, RECHAZADA, CANCELADA));
        TRANSICIONES.put(EN_REVISION, EnumSet.of(APROBADA, RECHAZADA, CANCELADA));
        // PROGRAMADA la fija la creación de la cirugía, no un endpoint de la solicitud
        TRANSICIONES.put(APROBADA, EnumSet.of(PROGRAMADA, CANCELADA));
        TRANSICIONES.put(PROGRAMADA, EnumSet.noneOf(EstadoSolicitudCirugia.class));
        TRANSICIONES.put(RECHAZADA, EnumSet.noneOf(EstadoSolicitudCirugia.class));
        TRANSICIONES.put(CANCELADA, EnumSet.noneOf(EstadoSolicitudCirugia.class));
    }

    public boolean puedePasarA(EstadoSolicitudCirugia destino) {
        return TRANSICIONES.get(this).contains(destino);
    }

    /** Los datos clínicos se editan hasta que la enfermera jefe empieza a revisarla. */
    public boolean admiteEdicion() {
        return this == BORRADOR || this == ENVIADA;
    }

    /**
     * El médico puede ajustar los roles requeridos mientras la solicitud siga viva y sin programar.
     * Con la solicitud PROGRAMADA, SolicitudesService además lo permite mientras la cirugía no haya empezado.
     */
    public boolean admiteCambiosEnRequerimientos() {
        return this != PROGRAMADA && this != RECHAZADA && this != CANCELADA;
    }
}
