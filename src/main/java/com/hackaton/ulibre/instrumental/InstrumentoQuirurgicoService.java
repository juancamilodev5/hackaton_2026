package com.hackaton.ulibre.instrumental;

import com.hackaton.ulibre.comun.catalogo.CatalogoService;
import org.springframework.stereotype.Service;

@Service
public class InstrumentoQuirurgicoService extends CatalogoService<InstrumentoQuirurgico> {

    public InstrumentoQuirurgicoService(InstrumentoQuirurgicoRepository repositorio) {
        super(repositorio, InstrumentoQuirurgico::new, "Instrumento no encontrado");
    }
}
