package com.hackaton.ulibre.checklist;

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

/** registradoPorAsignacionId: quién digitó la respuesta en el tablero (no quién la confirma clínicamente). */
@Entity
@Table(name = "items_checklist_cirugia")
@DynamicUpdate
@Getter
@Setter
public class ItemChecklistCirugia {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID cirugiaId;
    private UUID faseCirugiaId;
    private UUID itemPlantillaOrigenId;
    private String codigoItem;
    private String etiqueta;
    private String descripcion;
    private String tipoRespuesta;
    private boolean obligatorio;
    private boolean bloqueante;
    private UUID rolClinicoResponsableId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "estado_item_checklist")
    private EstadoItemChecklist estado;

    /** JSON crudo: la forma depende de tipo_respuesta. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String respuesta;

    private UUID registradoPorAsignacionId;
    private LocalDateTime registradoEn;
    private String notas;
    private Integer orden;

    @Generated
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
