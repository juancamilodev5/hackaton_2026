package com.hackaton.ulibre.alertas;

import java.util.List;
import java.util.UUID;

import com.hackaton.ulibre.auditoria.AccionAuditoria;
import com.hackaton.ulibre.auditoria.Auditoria;
import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Solo se configura severidad, bloqueo, textos y activación; las alertas ya disparadas conservan sus valores. */
@Service
public class ReglaSeguridadService {

    private final ReglaSeguridadRepository reglas;
    private final Auditoria auditoria;

    public ReglaSeguridadService(ReglaSeguridadRepository reglas, Auditoria auditoria) {
        this.reglas = reglas;
        this.auditoria = auditoria;
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
        ReglaSeguridadResponse anterior = ReglaSeguridadResponse.de(regla);
        regla.setNombre(Textos.limpiar(datos.nombre()));
        regla.setDescripcion(Textos.limpiar(datos.descripcion()));
        regla.setSeveridad(datos.severidad());
        regla.setBloqueante(datos.bloqueante());
        if (datos.activo() != null) {
            regla.setActivo(datos.activo());
        }
        ReglaSeguridadResponse actualizada = ReglaSeguridadResponse.de(reglas.saveAndFlush(regla));
        auditoria.registrar("reglas_seguridad", id, AccionAuditoria.ACTUALIZAR, anterior, actualizada);
        return actualizada;
    }

    private ReglaSeguridad buscar(UUID id) {
        return reglas.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Regla de seguridad no encontrada: " + id));
    }
}
