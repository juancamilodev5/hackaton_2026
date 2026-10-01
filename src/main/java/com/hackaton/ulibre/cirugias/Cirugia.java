package com.hackaton.ulibre.cirugias;

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
 * Solapes de quirófano (EXCLUDE), re-validación de personal al cambiar horario e instrumental
 * obligatorio al pasar a EN_CIRUGIA los garantiza la base. @DynamicUpdate evita disparar los
 * triggers "UPDATE OF columna" con columnas que no cambiaron.
 */
@Entity
@Table(name = "cirugias")
@DynamicUpdate
@Getter
@Setter
public class Cirugia {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID solicitudCirugiaId;
    private UUID quirofanoId;
    private UUID programadaPorUsuarioId;
    private UUID coordinadorUsuarioId;
    private LocalDateTime inicioProgramado;
    private LocalDateTime finProgramado;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "estado_cirugia")
    private EstadoCirugia estado;

    private String notasProgramacion;

    @Generated
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
