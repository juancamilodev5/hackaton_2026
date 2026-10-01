package com.hackaton.ulibre.solicitudes;

import com.hackaton.ulibre.cirugias.Lateralidad;
import jakarta.validation.constraints.Size;

/** Edición de los datos clínicos de una solicitud (BORRADOR o ENVIADA). lateralidad vacía → NO_APLICA. */
public record DatosClinicosRequest(
        @Size(max = 150) String sitioQuirurgico,
        Lateralidad lateralidad,
        @Size(max = 10000) String resumenClinico,
        @Size(max = 10000) String notasMedicas) {
}
