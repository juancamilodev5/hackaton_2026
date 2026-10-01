package com.hackaton.ulibre.protocolos;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/** Solo editable si la plantilla está en BORRADOR (trigger tg_items_plantilla_proteger). */
@Entity
@Table(name = "items_plantilla_checklist")
@DynamicUpdate
@Getter
@Setter
public class ItemPlantillaChecklist {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID faseId;
    private String codigo;
    private String etiqueta;
    private String descripcion;
    private String tipoRespuesta;
    private boolean obligatorio;
    private boolean bloqueante;
    private UUID rolClinicoResponsableId;

    /** JSON crudo con la configuración de validación de la respuesta. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String configValidacion;

    private int orden;

    @Generated
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
