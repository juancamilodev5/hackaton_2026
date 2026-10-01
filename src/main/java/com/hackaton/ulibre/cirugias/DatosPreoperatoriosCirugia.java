package com.hackaton.ulibre.cirugias;

import java.math.BigDecimal;
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

/** Snapshot preoperatorio: conserva lo que se usó en la cirugía aunque cambie el paciente. */
@Entity
@Table(name = "datos_preoperatorios_cirugia")
@DynamicUpdate
@Getter
@Setter
public class DatosPreoperatoriosCirugia {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID cirugiaId;
    private BigDecimal pesoKg;
    private BigDecimal tallaCm;
    private BigDecimal glucometriaMgDl;
    private boolean requiereReservaSangre;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "estado_reserva_sangre")
    private EstadoReservaSangre estadoReservaSangre;

    private String informacionClinicaRelevante;

    /** JSON crudo: arreglo de {sustancia, reaccion, severidad}. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String copiaAlergias;

    @JdbcTypeCode(SqlTypes.JSON)
    private String datosAdicionales;

    private UUID validadoPorUsuarioId;
    private LocalDateTime validadoEn;

    @Generated
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private LocalDateTime actualizadoEn;
}
