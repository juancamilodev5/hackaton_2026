package com.hackaton.ulibre.comun;

import java.util.Locale;

/** Normalización de textos de entrada antes de guardarlos. */
public final class Textos {

    private Textos() {
    }

    /** Recorta espacios; cadena vacía → null (para columnas opcionales). */
    public static String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.strip();
        return limpio.isEmpty() ? null : limpio;
    }

    /** Códigos de catálogo en mayúsculas, como los de la semilla (CIRUJANO, QUIROFANO_01...). */
    public static String codigo(String valor) {
        String limpio = limpiar(valor);
        return limpio == null ? null : limpio.toUpperCase(Locale.ROOT);
    }

    /**
     * Patrón "contiene" para LIKE/ILIKE con los comodines del usuario escapados (\ es el escape
     * por defecto de PostgreSQL). null si no hay texto que buscar.
     */
    public static String patronLike(String valor) {
        String limpio = limpiar(valor);
        if (limpio == null) {
            return null;
        }
        return "%" + limpio.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** Correos en minúsculas: el login los busca sin distinguir mayúsculas. */
    public static String correo(String valor) {
        String limpio = limpiar(valor);
        return limpio == null ? null : limpio.toLowerCase(Locale.ROOT);
    }
}
