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

/**
 * Snapshot de la plantilla para una cirugía. Se crea con fn_iniciar_checklist (nunca desde Java);
 * pasar a EN_PROGRESO sin operador del tablero lo impide tg_checklists_inicio_tablero.
 */
@Entity
@Table(name = "checklists_cirugia")
@DynamicUpdate
@Getter
@Setter
public class ChecklistCirugia {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID cirugiaId;
    private UUID plantillaOrigenId;
    private String codigoPlantilla;
    private String nombrePlantilla;
    private Integer versionPlantilla;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "estado_checklist")
    private EstadoChecklist estado;

    private LocalDateTime iniciadoEn;
    private LocalDateTime completadoEn;

    @Generated
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
