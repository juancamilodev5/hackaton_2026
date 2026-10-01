package com.hackaton.ulibre.solicitudes;

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

/** Lo que el médico pidió para ESTA solicitud (no la configuración del procedimiento). cantidad > 0 por CHECK. */
@Entity
@Table(name = "requerimientos_roles_solicitud")
@DynamicUpdate
@Getter
@Setter
public class RequerimientoRolSolicitud {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID solicitudCirugiaId;
    private UUID rolClinicoId;
    private UUID especialidadId;
    private int cantidad;
    private boolean esRequerido;
    private String notas;

    @Generated
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
