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
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/**
 * Configuración de una regla clínica. La lógica vive en el backend, identificada por {@code codigo};
 * por eso el código no se edita y las reglas no se crean ni borran desde la API.
 */
@Entity
@Table(name = "reglas_seguridad")
@DynamicUpdate
@Getter
@Setter
public class ReglaSeguridad {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String codigo;
    private String nombre;
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "severidad_alerta")
    private SeveridadAlerta severidad;

    private boolean bloqueante;

    /** Reservado para reglas configurables; JSON crudo. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String condicion;

    private boolean activo;

    @Generated
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
