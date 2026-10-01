package com.hackaton.ulibre.pacientes;

import java.time.LocalDate;
import java.util.UUID;

/** Fila del listado de pacientes. */
public record PacienteResumen(
        UUID id,
        String nombres,
        String apellidos,
        String tipoDocumento,
        String numeroDocumento,
        LocalDate fechaNacimiento,
        Integer edad,
        boolean tieneCuenta) {
}
