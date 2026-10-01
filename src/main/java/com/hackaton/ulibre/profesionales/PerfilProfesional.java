package com.hackaton.ulibre.profesionales;

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
 * Un usuario habilitado como profesional (uno por usuario: uq_perfiles_profesionales_usuario).
 * Un perfil inactivo no puede asignarse a cirugías (lo comprueba tg_asignaciones_validar).
 */
@Entity
@Table(name = "perfiles_profesionales")
@DynamicUpdate
@Getter
@Setter
public class PerfilProfesional {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID usuarioId;
    private String licenciaProfesional;
    private String numeroProfesional;
    private boolean activo;

    @Generated
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
