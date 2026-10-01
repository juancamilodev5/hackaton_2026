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
import org.hibernate.type.SqlTypes;

/** Que quien confirma cubra el rol clínico del ítem lo valida tg_confirmaciones_item_rol. */
@Entity
@Table(name = "confirmaciones_item_checklist")
@DynamicUpdate
@Getter
@Setter
public class ConfirmacionItemChecklist {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID cirugiaId;
    private UUID itemChecklistCirugiaId;
    private UUID asignacionPersonalId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "resultado_confirmacion")
    private ResultadoConfirmacion resultado;

    private LocalDateTime confirmadoEn;
    private String notas;

    @Generated
    private LocalDateTime creadoEn;
}
