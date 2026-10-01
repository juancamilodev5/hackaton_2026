package com.hackaton.ulibre.cirugias;

import java.time.LocalDate;
import java.util.UUID;

/** La edad se calcula al consultar desde fecha_nacimiento; nunca se guarda. */
public record PacienteVista(
        UUID id,
        String nombreCompleto,
        String tipoDocumento,
        String numeroDocumento,
        LocalDate fechaNacimiento,
        Integer edad) {
}
