package com.hackaton.ulibre.portal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Datos propios del paciente autenticado; la edad se calcula (regla 19). */
public record MiPacienteResponse(
        UUID id,
        String nombres,
        String apellidos,
        String tipoDocumento,
        String numeroDocumento,
        LocalDate fechaNacimiento,
        Integer edad,
        String telefono,
        String correo,
        String contactoEmergenciaNombre,
        String contactoEmergenciaTelefono,
        List<Alergia> alergias) {

    public record Alergia(String sustancia, String reaccion, String severidad) {
    }
}
