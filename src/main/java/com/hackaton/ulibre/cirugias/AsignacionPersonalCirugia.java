package com.hackaton.ulibre.cirugias;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Rol clínico, especialidad, cupo, solapes y disponibilidad los valida el trigger
 * tg_asignaciones_validar; un único operador del tablero vigente, el índice ux_cirugia_operador_tablero.
 */
@Entity
@Table(name = "asignaciones_personal_cirugia")
@DynamicUpdate
@Getter
@Setter
public class AsignacionPersonalCirugia {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID cirugiaId;
    /** Solo amarra cirugía y requerimiento a la misma solicitud (FKs compuestas). */
    private UUID solicitudCirugiaId;
    private UUID requerimientoRolId;
    private UUID profesionalId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "estado_asignacion")
    private EstadoAsignacion estado;

    private boolean esOperadorTablero;
    private UUID asignadoPorUsuarioId;
    private LocalDateTime asignadoEn;
    private LocalDateTime confirmadoEn;
    private String notas;
}
