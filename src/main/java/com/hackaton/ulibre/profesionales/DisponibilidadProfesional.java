package com.hackaton.ulibre.profesionales;

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
 * Bloque de disponibilidad explícita. termina_en > inicia_en lo exige ck_disponibilidad_rango; un
 * bloque NO_DISPONIBLE impide asignar al profesional en ese horario (tg_asignaciones_validar).
 */
@Entity
@Table(name = "disponibilidad_profesional")
@DynamicUpdate
@Getter
@Setter
public class DisponibilidadProfesional {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID profesionalId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "tipo_disponibilidad")
    private TipoDisponibilidad tipoDisponibilidad;

    private LocalDateTime iniciaEn;
    private LocalDateTime terminaEn;
    private String notas;

    @Generated
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
