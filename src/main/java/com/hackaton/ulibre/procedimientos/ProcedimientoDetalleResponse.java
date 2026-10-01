package com.hackaton.ulibre.procedimientos;

import java.util.List;
import java.util.UUID;

/** Procedimiento con toda su configuración: especialidades, roles, plantillas e instrumental predeterminados. */
public record ProcedimientoDetalleResponse(
        ProcedimientoResponse procedimiento,
        List<Referencia> especialidades,
        List<RolPredeterminadoResponse> rolesPredeterminados,
        List<Plantilla> plantillasChecklist,
        List<Predeterminado> setsPredeterminados,
        List<Predeterminado> instrumentosPredeterminados) {

    public record Referencia(UUID id, String codigo, String nombre) {
    }

    public record Plantilla(UUID id, String codigo, String nombre, int version, String estado,
                            boolean esPredeterminada) {
    }

    /** Set o instrumento predeterminado del procedimiento. */
    public record Predeterminado(UUID id, String codigo, String nombre, int cantidad, boolean esRequerido,
                                 String notas) {
    }
}
