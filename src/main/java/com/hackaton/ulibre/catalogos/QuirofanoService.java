package com.hackaton.ulibre.catalogos;

import com.hackaton.ulibre.comun.catalogo.CatalogoService;
import org.springframework.stereotype.Service;

@Service
public class QuirofanoService extends CatalogoService<Quirofano> {

    public QuirofanoService(QuirofanoRepository repositorio) {
        super(repositorio, Quirofano::new, "Quirófano no encontrado");
    }
}
