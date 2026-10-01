package com.hackaton.ulibre.alertas;

/** Enum de PostgreSQL {@code estado_alerta}. Vigentes: ABIERTA y RECONOCIDA. */
public enum EstadoAlerta {
    ABIERTA, RECONOCIDA, RESUELTA, DESCARTADA;

    public boolean vigente() {
        return this == ABIERTA || this == RECONOCIDA;
    }
}
