package com.hackaton.ulibre.cirugias;

import java.util.List;

/** equipoCompleto: todos los requerimientos es_requerido cubiertos en su cantidad. */
public record EquipoVista(boolean equipoCompleto, List<RequerimientoVista> requerimientos) {
}
