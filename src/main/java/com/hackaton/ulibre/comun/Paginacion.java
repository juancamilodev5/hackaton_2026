package com.hackaton.ulibre.comun;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Parámetros {@code pagina} y {@code tamano} ya normalizados: página negativa → 0 y tamaño
 * recortado a 1..{@value #TAMANO_MAXIMO} (no se responde 400 por pedir de más).
 */
public record Paginacion(int pagina, int tamano) {

    public static final int TAMANO_MAXIMO = 100;

    public static Paginacion de(int pagina, int tamano) {
        return new Paginacion(Math.max(pagina, 0), Math.clamp(tamano, 1, TAMANO_MAXIMO));
    }

    /** OFFSET para las consultas nativas. */
    public long desplazamiento() {
        return (long) pagina * tamano;
    }

    public PageRequest pageRequest(Sort orden) {
        return PageRequest.of(pagina, tamano, orden);
    }
}
