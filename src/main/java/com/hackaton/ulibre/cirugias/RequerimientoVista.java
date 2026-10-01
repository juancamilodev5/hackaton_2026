package com.hackaton.ulibre.cirugias;

import java.util.List;
import java.util.UUID;

import com.hackaton.ulibre.catalogos.RolClinicoVista;

/** Un requerimiento de rol de la solicitud y las asignaciones vigentes (ASIGNADA/CONFIRMADA) que lo cubren. */
public record RequerimientoVista(
        UUID id,
        RolClinicoVista rol,
        String especialidad,
        int cantidadRequerida,
        boolean esRequerido,
        int cubiertos,
        List<AsignacionVista> asignaciones) {

    public boolean cubierto() {
        return cubiertos >= cantidadRequerida;
    }
}
