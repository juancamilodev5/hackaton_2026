package com.hackaton.ulibre.tablero;

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
import org.hibernate.type.SqlTypes;

/**
 * Un hito no se edita ni se borra: solo se anula una vez (tg_hitos_proteger) y se registra uno nuevo
 * con corrigeHitoId. Un solo hito vigente por tipo (uq_hitos_vigentes).
 */
@Entity
@Table(name = "hitos_cirugia")
@DynamicUpdate
@Getter
@Setter
public class HitoCirugia {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID cirugiaId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "tipo_hito")
    private TipoHito tipoHito;

    private LocalDateTime ocurridoEn;
    private UUID registradoPorAsignacionId;
    private String notas;
    private UUID corrigeHitoId;
    private LocalDateTime anuladoEn;
    private UUID anuladoPorUsuarioId;
    private String motivoAnulacion;

    @Generated
    private LocalDateTime creadoEn;
}
