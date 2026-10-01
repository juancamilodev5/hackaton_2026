package com.hackaton.ulibre.protocolos;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UuidGenerator;

/** Solo editable si la plantilla está en BORRADOR (trigger tg_fases_plantilla_proteger). */
@Entity
@Table(name = "fases_plantilla_checklist")
@DynamicUpdate
@Getter
@Setter
public class FasePlantillaChecklist {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID plantillaId;
    private String codigo;
    private String nombre;
    private String descripcion;
    private int orden;
}
