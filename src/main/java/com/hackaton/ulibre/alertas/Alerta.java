package com.hackaton.ulibre.alertas;

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
 * Cierre y excepción completos (quién + cuándo + motivo) los exigen los CHECK de la tabla; una sola
 * alerta vigente por regla y cirugía, el índice ux_alerta_vigente_por_regla (V3).
 */
@Entity
@Table(name = "alertas")
@DynamicUpdate
@Getter
@Setter
public class Alerta {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID cirugiaId;
    private UUID reglaSeguridadId;
    private String tipoOrigen;
    private UUID origenId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "severidad_alerta")
    private SeveridadAlerta severidad;

    private boolean bloqueante;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "estado_alerta")
    private EstadoAlerta estado;

    private String titulo;
    private String mensaje;
    private LocalDateTime disparadaEn;
    private UUID reconocidaPorUsuarioId;
    private LocalDateTime reconocidaEn;
    private UUID resueltaPorUsuarioId;
    private LocalDateTime resueltaEn;
    private String notasResolucion;
    private UUID excepcionAutorizadaPorUsuarioId;
    private LocalDateTime excepcionAutorizadaEn;
    private String motivoExcepcion;
}
