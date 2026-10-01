package com.hackaton.ulibre.catalogos;

import com.hackaton.ulibre.comun.catalogo.CatalogoService;
import org.springframework.stereotype.Service;

@Service
public class EspecialidadService extends CatalogoService<Especialidad> {

    public EspecialidadService(EspecialidadRepository repositorio) {
        super(repositorio, Especialidad::new, "Especialidad no encontrada");
    }
}
