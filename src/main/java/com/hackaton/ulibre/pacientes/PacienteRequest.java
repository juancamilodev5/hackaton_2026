package com.hackaton.ulibre.pacientes;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/** Alta y edición de un paciente. usuarioId vincula la cuenta de acceso (opcional). */
public record PacienteRequest(
        UUID usuarioId,
        @NotBlank @Size(max = 100) String nombres,
        @NotBlank @Size(max = 100) String apellidos,
        @NotBlank @Size(max = 30) String tipoDocumento,
        @NotBlank @Size(max = 50) String numeroDocumento,
        @PastOrPresent(message = "no puede ser una fecha futura") LocalDate fechaNacimiento,
        @Size(max = 50) String telefono,
        @Email @Size(max = 180) String correo,
        @Size(max = 150) String contactoEmergenciaNombre,
        @Size(max = 50) String contactoEmergenciaTelefono) {
}
