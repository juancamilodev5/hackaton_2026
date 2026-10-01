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

/** Completar una fase con alertas bloqueantes abiertas lo impide tg_fases_cierre_alertas. */
@Entity
@Table(name = "fases_checklist_cirugia")
@DynamicUpdate
@Getter
@Setter
public class FaseChecklistCirugia {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID cirugiaId;
    private UUID checklistCirugiaId;
    private UUID fasePlantillaOrigenId;
    private String codigoFase;
    private String nombreFase;
    private Integer orden;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "estado_fase_checklist")
    private EstadoFaseChecklist estado;

    private LocalDateTime iniciadaEn;
    private UUID cerradaPorAsignacionId;
    private LocalDateTime cerradaEn;
    private String notas;

    @Generated
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
