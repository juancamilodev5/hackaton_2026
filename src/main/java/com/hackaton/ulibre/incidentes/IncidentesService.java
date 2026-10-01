package com.hackaton.ulibre.incidentes;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.ParticipacionCirugia;
import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.Textos;
import com.hackaton.ulibre.comun.UsuarioActual;
import com.hackaton.ulibre.eventos.EventosCirugia;
import com.hackaton.ulibre.eventos.TipoEvento;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentesService {

    private final IncidenteRepository incidentes;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final EventosCirugia eventos;
    private final ParticipacionCirugia participacion;

    public IncidentesService(IncidenteRepository incidentes, JdbcClient jdbc, Clock clock, EventosCirugia eventos,
            ParticipacionCirugia participacion) {
        this.incidentes = incidentes;
        this.jdbc = jdbc;
        this.clock = clock;
        this.eventos = eventos;
        this.participacion = participacion;
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
        incidentes.saveAndFlush(incidente);
        registrar(incidente, TipoEvento.INCIDENTE_REPORTADO, null);
        return IncidenteResponse.de(incidente);
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
        EstadoIncidente anterior = incidente.getEstado();
        incidente.setEstado(datos.estado());
        incidentes.saveAndFlush(incidente);
        registrar(incidente, TipoEvento.INCIDENTE_ACTUALIZADO, anterior);
        return IncidenteResponse.de(incidente);
    }

    /** Sin la descripción (texto clínico libre): solo categoría, severidad y estado. */
    private void registrar(Incidente incidente, TipoEvento tipo, EstadoIncidente anterior) {
        Map<String, Object> datos = new LinkedHashMap<>();
        if (incidente.getCategoria() != null) {
            datos.put("categoria", incidente.getCategoria());
        }
        if (incidente.getSeveridad() != null) {
            datos.put("severidad", incidente.getSeveridad().name());
        }
        if (anterior != null) {
            datos.put("de", anterior.name());
        }
        datos.put("estado", incidente.getEstado().name());
        eventos.registrar(incidente.getCirugiaId(), tipo,
                participacion.asignacionVigente(incidente.getCirugiaId()).orElse(null), "incidentes",
                incidente.getId(), datos);
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
