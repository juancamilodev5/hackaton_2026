package com.hackaton.ulibre.usuarios;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UuidGenerator;

/** Rol del sistema (qué puede hacer en la aplicación), distinto del rol clínico. Código único por UNIQUE. */
@Entity
@Table(name = "roles_sistema")
@DynamicUpdate
@Getter
@Setter
public class RolSistema {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String codigo;
    private String nombre;
    private String descripcion;
    private boolean activo;
}
