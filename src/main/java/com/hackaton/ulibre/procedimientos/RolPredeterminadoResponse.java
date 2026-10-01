package com.hackaton.ulibre.procedimientos;

import java.util.UUID;

public record RolPredeterminadoResponse(
        UUID id,
        UUID rolClinicoId,
        String rolClinicoCodigo,
        String rolClinicoNombre,
        UUID especialidadId,
        String especialidadCodigo,
        String especialidadNombre,
        int cantidadPredeterminada,
        boolean esRequerido,
        String notas) {
}
