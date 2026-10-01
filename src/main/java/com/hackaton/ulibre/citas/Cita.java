package com.hackaton.ulibre.citas;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/**
 * Consulta con un médico (perfil profesional) de una especialidad. La base no valida que el médico
 * tenga la especialidad ni que esté activo: lo comprueba CitaService.
 */
@Entity
@Table(name = "citas")
@DynamicUpdate
@Getter
@Setter
public class Cita {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID pacienteId;
    private UUID medicoId;
    private UUID especialidadId;
    private LocalDateTime programadaPara;
    private String motivo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "estado_cita")
    private EstadoCita estado;

    @Generated
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
