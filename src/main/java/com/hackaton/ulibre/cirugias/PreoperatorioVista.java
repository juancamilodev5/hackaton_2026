package com.hackaton.ulibre.cirugias;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PreoperatorioVista(
        BigDecimal pesoKg,
        BigDecimal tallaCm,
        BigDecimal glucometriaMgDl,
        boolean requiereReservaSangre,
        EstadoReservaSangre estadoReservaSangre,
        /** Snapshot tomado al preparar la cirugía (copia_alergias), no las alergias actuales del paciente. */
        List<AlergiaVista> alergias,
        String informacionClinicaRelevante,
        String validadoPor,
        LocalDateTime validadoEn) {
}
