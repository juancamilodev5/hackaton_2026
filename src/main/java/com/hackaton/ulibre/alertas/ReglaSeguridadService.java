package com.hackaton.ulibre.alertas;

import java.util.List;
import java.util.UUID;

import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Solo se configura severidad, bloqueo, textos y activación; las alertas ya disparadas conservan sus valores. */
@Service
public class ReglaSeguridadService {

    private final ReglaSeguridadRepository reglas;

    public ReglaSeguridadService(ReglaSeguridadRepository reglas) {
        this.reglas = reglas;
    }

    @Transactional(readOnly = true)
    public List<ReglaSeguridadResponse> listar(Boolean activo) {
        List<ReglaSeguridad> filas = activo == null ? reglas.findAllByOrderByCodigo()
                : reglas.findAllByActivoOrderByCodigo(activo);
        return filas.stream().map(ReglaSeguridadResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public ReglaSeguridadResponse obtener(UUID id) {
        return ReglaSeguridadResponse.de(buscar(id));
    }

    @Transactional
    public ReglaSeguridadResponse actualizar(UUID id, ReglaSeguridadRequest datos) {
        ReglaSeguridad regla = buscar(id);
        regla.setNombre(Textos.limpiar(datos.nombre()));
        regla.setDescripcion(Textos.limpiar(datos.descripcion()));
        regla.setSeveridad(datos.severidad());
        regla.setBloqueante(datos.bloqueante());
        if (datos.activo() != null) {
            regla.setActivo(datos.activo());
        }
        return ReglaSeguridadResponse.de(reglas.saveAndFlush(regla));
    }

    private ReglaSeguridad buscar(UUID id) {
        return reglas.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Regla de seguridad no encontrada: " + id));
    }
}
