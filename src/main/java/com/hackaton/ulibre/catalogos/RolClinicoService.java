package com.hackaton.ulibre.catalogos;

import com.hackaton.ulibre.comun.catalogo.CatalogoService;
import org.springframework.stereotype.Service;

@Service
public class RolClinicoService extends CatalogoService<RolClinico> {

    public RolClinicoService(RolClinicoRepository repositorio) {
        super(repositorio, RolClinico::new, "Rol clínico no encontrado");
    }
}
