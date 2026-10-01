package com.hackaton.ulibre.instrumental;

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

/** Instrumento del snapshot: dentro de un set (setCirugiaId) o requerido directamente (setCirugiaId null). */
@Entity
@Table(name = "instrumentos_cirugia")
@DynamicUpdate
@Getter
@Setter
public class InstrumentoCirugia {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID cirugiaId;
    private UUID setCirugiaId;
    private UUID instrumentoOrigenId;
    private String codigoInstrumento;
    private String nombreInstrumento;
    private boolean esRequerido;
    private Integer cantidadRequerida;
    private Integer cantidadPreparada;
    private UUID preparadoPorAsignacionId;
    private LocalDateTime preparadoEn;
    private String notas;

    @Generated
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
