package com.hackaton.ulibre.protocolos;

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
 * Regla 10: una plantilla PUBLICADA o RETIRADA no se edita ni se borra y solo admite
 * PUBLICADA → RETIRADA (trigger tg_plantillas_proteger). Para cambiarla se crea una nueva versión.
 */
@Entity
@Table(name = "plantillas_checklist")
@DynamicUpdate
@Getter
@Setter
public class PlantillaChecklist {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String codigo;
    private String nombre;
    private String descripcion;
    private int version;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "estado_plantilla")
    private EstadoPlantilla estado;

    private LocalDateTime publicadaEn;

    @Generated
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
