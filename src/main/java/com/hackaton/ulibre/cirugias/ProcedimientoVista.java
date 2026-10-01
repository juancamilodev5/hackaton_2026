package com.hackaton.ulibre.cirugias;

import java.util.UUID;

/** Procedimiento solicitado, con el sitio quirúrgico y la lateralidad de la solicitud. */
public record ProcedimientoVista(
        UUID id,
        String codigo,
        String nombre,
        String sitioQuirurgico,
        Lateralidad lateralidad) {
}
