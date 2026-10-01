package com.hackaton.ulibre.catalogos;

import com.hackaton.ulibre.comun.catalogo.Catalogo;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/** Desactivar un quirófano no cancela sus cirugías: solo deja de ofrecerse para programar. */
@Entity
@Table(name = "quirofanos")
@DynamicUpdate
public class Quirofano extends Catalogo {
}
