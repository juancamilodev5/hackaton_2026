package com.hackaton.ulibre.incidentes;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.Textos;
import com.hackaton.ulibre.comun.UsuarioActual;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentesService {

    private final IncidenteRepository incidentes;
    private final JdbcClient jdbc;
    private final Clock clock;

    public IncidentesService(IncidenteRepository incidentes, JdbcClient jdbc, Clock clock) {
        this.incidentes = incidentes;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<IncidenteResponse> listar(UUID cirugiaId) {
        exigirCirugia(cirugiaId);
        return incidentes.findAllByCirugiaIdOrderByReportadoEnDesc(cirugiaId).stream()
                .map(IncidenteResponse::de)
                .toList();
    }

    @Transactional
    public IncidenteResponse reportar(UUID cirugiaId, IncidenteRequest datos) {
        exigirCirugia(cirugiaId);
        Incidente incidente = new Incidente();
        incidente.setCirugiaId(cirugiaId);
        incidente.setCategoria(Textos.limpiar(datos.categoria()));
        incidente.setSeveridad(datos.severidad());
        incidente.setDescripcion(Textos.limpiar(datos.descripcion()));
        incidente.setEstado(EstadoIncidente.ABIERTO);
        incidente.setReportadoPorUsuarioId(UsuarioActual.id());
        incidente.setReportadoEn(LocalDateTime.now(clock));
        return IncidenteResponse.de(incidentes.saveAndFlush(incidente));
    }

    /** Al pasar a RESUELTO se registra quién y cuándo; si se reabre, se limpian. */
    @Transactional
    public IncidenteResponse actualizar(UUID cirugiaId, UUID incidenteId, ActualizarIncidenteRequest datos) {
        Incidente incidente = incidentes.findByIdAndCirugiaId(incidenteId, cirugiaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Incidente no encontrado: " + incidenteId));
        incidente.setCategoria(Textos.limpiar(datos.categoria()));
        incidente.setSeveridad(datos.severidad());
        incidente.setDescripcion(Textos.limpiar(datos.descripcion()));
        incidente.setNotasResolucion(Textos.limpiar(datos.notasResolucion()));
        if (datos.estado() == EstadoIncidente.RESUELTO && incidente.getEstado() != EstadoIncidente.RESUELTO) {
            incidente.setResueltoPorUsuarioId(UsuarioActual.id());
            incidente.setResueltoEn(LocalDateTime.now(clock));
        } else if (datos.estado() != EstadoIncidente.RESUELTO) {
            incidente.setResueltoPorUsuarioId(null);
            incidente.setResueltoEn(null);
        }
        incidente.setEstado(datos.estado());
        return IncidenteResponse.de(incidentes.saveAndFlush(incidente));
    }

    private void exigirCirugia(UUID cirugiaId) {
        boolean existe = jdbc.sql("SELECT EXISTS (SELECT 1 FROM cirugias WHERE id = :id)")
                .param("id", cirugiaId)
                .query(Boolean.class)
                .single();
        if (!existe) {
            throw new RecursoNoEncontradoException("Cirugía no encontrada: " + cirugiaId);
        }
    }
}
