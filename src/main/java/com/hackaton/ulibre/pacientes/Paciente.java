package com.hackaton.ulibre.pacientes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;

/**
 * Paciente separado de usuario: puede existir sin cuenta (usuario_id NULL). Documento y usuario
 * únicos por uq_pacientes_documento y uq_pacientes_usuario. La edad no se guarda (regla 19).
 */
@Entity
@Table(name = "pacientes")
@DynamicUpdate
@Getter
@Setter
public class Paciente {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID usuarioId;
    private String nombres;
    private String apellidos;
    private String tipoDocumento;
    private String numeroDocumento;
    private LocalDate fechaNacimiento;
    private String telefono;
    private String correo;
    private String contactoEmergenciaNombre;
    private String contactoEmergenciaTelefono;

    @Generated
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
