package com.hackaton.ulibre.cirugias;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record PreoperatorioResponse(
        UUID id,
        UUID cirugiaId,
        BigDecimal pesoKg,
        BigDecimal tallaCm,
        BigDecimal glucometriaMgDl,
        boolean requiereReservaSangre,
        EstadoReservaSangre estadoReservaSangre,
        String informacionClinicaRelevante,
        /** Snapshot de las alergias activas del paciente: [{sustancia, reaccion, severidad}]. */
        @JsonRawValue String copiaAlergias,
        @JsonRawValue String datosAdicionales,
        UUID validadoPorUsuarioId,
        String validadoPor,
        LocalDateTime validadoEn,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn) {

    static PreoperatorioResponse de(DatosPreoperatoriosCirugia d, String validadoPor) {
        return new PreoperatorioResponse(d.getId(), d.getCirugiaId(), d.getPesoKg(), d.getTallaCm(),
                d.getGlucometriaMgDl(), d.isRequiereReservaSangre(), d.getEstadoReservaSangre(),
                d.getInformacionClinicaRelevante(), d.getCopiaAlergias(), d.getDatosAdicionales(),
                d.getValidadoPorUsuarioId(), validadoPor, d.getValidadoEn(), d.getCreadoEn(), d.getActualizadoEn());
    }
}
