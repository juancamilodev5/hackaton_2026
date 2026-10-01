package com.hackaton.ulibre.tablero;

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

/** El estado (PENDIENTE/CUADRA/DISCREPANCIA) no se guarda: se lee de v_recuentos_cirugia. */
@Entity
@Table(name = "recuentos_cirugia")
@DynamicUpdate
@Getter
@Setter
public class RecuentoCirugia {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID cirugiaId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "tipo_recuento")
    private TipoRecuento tipoRecuento;

    private UUID instrumentoCirugiaId;
    private String descripcion;
    private Integer cantidadInicial;
    private UUID registradoInicialPorAsignacionId;
    private LocalDateTime registradoInicialEn;
    private Integer cantidadAgregada;
    private Integer cantidadFinal;
    private UUID registradoFinalPorAsignacionId;
    private LocalDateTime registradoFinalEn;
    private String notas;

    @Generated
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
