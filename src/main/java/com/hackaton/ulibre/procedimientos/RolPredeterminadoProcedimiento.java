package com.hackaton.ulibre.procedimientos;

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
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;

/** Configuración sugerida para el procedimiento; la solicitud la copia y el médico la ajusta. */
@Entity
@Table(name = "roles_predeterminados_procedimiento")
@DynamicUpdate
@Getter
@Setter
public class RolPredeterminadoProcedimiento {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID procedimientoId;
    private UUID rolClinicoId;
    private UUID especialidadId;
    private int cantidadPredeterminada;
    private boolean esRequerido;
    private String notas;

    @Generated
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
