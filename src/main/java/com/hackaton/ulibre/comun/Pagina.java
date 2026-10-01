package com.hackaton.ulibre.comun;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Página de resultados con forma estable para el frontend (no se serializa {@code Page} directamente). */
public record Pagina<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) {

    public static <E, T> Pagina<T> de(Page<E> page, Function<E, T> mapeo) {
        return new Pagina<>(page.getContent().stream().map(mapeo).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    /** Para consultas nativas que traen la página y el total por separado. */
    public static <T> Pagina<T> de(List<T> contenido, Paginacion paginacion, long total) {
        int totalPaginas = (int) ((total + paginacion.tamano() - 1) / paginacion.tamano());
        return new Pagina<>(contenido, paginacion.pagina(), paginacion.tamano(), total, totalPaginas);
    }
}
