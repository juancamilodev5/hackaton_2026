package com.hackaton.ulibre.instrumental;

import com.hackaton.ulibre.comun.catalogo.CatalogoService;
import org.springframework.stereotype.Service;

@Service
public class SetInstrumentalService extends CatalogoService<SetInstrumental> {

    public SetInstrumentalService(SetInstrumentalRepository repositorio) {
        super(repositorio, SetInstrumental::new, "Set instrumental no encontrado");
    }
}
