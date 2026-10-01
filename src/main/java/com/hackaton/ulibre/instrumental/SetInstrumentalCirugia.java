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

/** Snapshot de un set predeterminado del procedimiento. Lo crea fn_preparar_instrumental. */
@Entity
@Table(name = "sets_instrumentales_cirugia")
@DynamicUpdate
@Getter
@Setter
public class SetInstrumentalCirugia {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID cirugiaId;
    private UUID setOrigenId;
    private String codigoSet;
    private String nombreSet;
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
