package com.hackaton.ulibre.comun.catalogo;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;

/**
 * Columnas comunes de los catálogos simples (roles clínicos, especialidades, quirófanos,
 * instrumentos, sets). La unicidad del código la garantiza el UNIQUE de cada tabla.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class Catalogo {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String codigo;
    private String nombre;
    private String descripcion;
    private boolean activo;

    @Generated
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
