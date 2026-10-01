package com.hackaton.ulibre.incidentes;

import java.time.LocalDateTime;
import java.util.UUID;

import com.hackaton.ulibre.alertas.SeveridadAlerta;
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
 * Novedad de una cirugía. Resuelto exige quién y cuándo (ck_incidente_resuelto); no se borra
 * (trigger fn_sin_borrado), se cancela.
 */
@Entity
@Table(name = "incidentes")
@DynamicUpdate
@Getter
@Setter
public class Incidente {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID cirugiaId;
    private String categoria;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "severidad_alerta")
    private SeveridadAlerta severidad;

    private String descripcion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "estado_incidente")
    private EstadoIncidente estado;

    private UUID reportadoPorUsuarioId;
    private LocalDateTime reportadoEn;
    private UUID resueltoPorUsuarioId;
    private LocalDateTime resueltoEn;
    private String notasResolucion;

    @Generated
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
