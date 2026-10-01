package com.hackaton.ulibre.instrumental;

import com.hackaton.ulibre.comun.catalogo.Catalogo;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "sets_instrumentales")
@DynamicUpdate
public class SetInstrumental extends Catalogo {
}
