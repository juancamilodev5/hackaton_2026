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

/** Código único (uq_procedimientos_codigo) y duración > 0 (ck_procedimientos_duracion) los garantiza la base. */
@Entity
@Table(name = "procedimientos_quirurgicos")
@DynamicUpdate
@Getter
@Setter
public class ProcedimientoQuirurgico {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String codigo;
    private String nombre;
    private String descripcion;
    private Integer duracionEstimadaMinutos;
    private boolean activo;

    @Generated
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
