package com.hackaton.ulibre.comun;

import java.time.ZoneId;

/** Zona de la institución. Todas las columnas timestamp de la base guardan hora local de esta zona. */
public final class ZonaHoraria {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private ZonaHoraria() {
    }
}
