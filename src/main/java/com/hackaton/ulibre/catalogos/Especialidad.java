package com.hackaton.ulibre.catalogos;

import com.hackaton.ulibre.comun.catalogo.Catalogo;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "especialidades")
@DynamicUpdate
public class Especialidad extends Catalogo {
}
