package com.hackaton.ulibre.solicitudes;

import java.time.LocalDateTime;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.Lateralidad;
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
 * Reglas 7 y 8 (procedimiento habilitado para la especialidad y médico de esa especialidad) las
 * garantizan las FKs compuestas fk_solicitud_procedimiento_especialidad y
 * fk_solicitud_medico_especialidad. Las transiciones de estado se validan en Java.
 */
@Entity
@Table(name = "solicitudes_cirugia")
@DynamicUpdate
@Getter
@Setter
public class SolicitudCirugia {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID pacienteId;
    private UUID medicoSolicitanteId;
    private UUID citaOrigenId;
    private UUID especialidadSolicitanteId;
    private UUID procedimientoId;
    private String sitioQuirurgico;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "lateralidad")
    private Lateralidad lateralidad;

    private String resumenClinico;
    private String notasMedicas;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "estado_solicitud_cirugia")
    private EstadoSolicitudCirugia estado;

    @Generated
    private LocalDateTime creadoEn;

    private LocalDateTime enviadaEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
