package com.hackaton.ulibre.pacientes;

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

/**
 * Dato maestro de alergias. Las cirugías guardan su propia copia en datos_preoperatorios_cirugia
 * (snapshot), así que editar o desactivar aquí no cambia el histórico.
 */
@Entity
@Table(name = "alergias_paciente")
@DynamicUpdate
@Getter
@Setter
public class AlergiaPaciente {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID pacienteId;
    private String sustancia;
    private String reaccion;
    private String severidad;
    private String notas;
    private boolean activo;

    @Generated
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
