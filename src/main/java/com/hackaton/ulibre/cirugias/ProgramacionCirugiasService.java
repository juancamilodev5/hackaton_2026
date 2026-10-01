package com.hackaton.ulibre.cirugias;

import static com.hackaton.ulibre.comun.Filas.enumeracion;
import static com.hackaton.ulibre.comun.Filas.entero;
import static com.hackaton.ulibre.comun.Filas.fechaHora;
import static com.hackaton.ulibre.comun.Filas.uuid;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.hackaton.ulibre.catalogos.Quirofano;
import com.hackaton.ulibre.catalogos.QuirofanoRepository;
import com.hackaton.ulibre.catalogos.QuirofanoVista;
import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.ReglaNegocioException;
import com.hackaton.ulibre.comun.Textos;
import com.hackaton.ulibre.comun.UsuarioActual;
import com.hackaton.ulibre.solicitudes.EstadoSolicitudCirugia;
import com.hackaton.ulibre.solicitudes.SolicitudCirugia;
import com.hackaton.ulibre.solicitudes.SolicitudCirugiaRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Programación de cirugías. Solapes de quirófano (EXCLUDE), una cirugía por solicitud (UNIQUE),
 * fin posterior al inicio (CHECK) y solapes del personal al reprogramar (trigger) los garantiza la base.
 */
@Service
public class ProgramacionCirugiasService {

    private static final DateTimeFormatter MARCA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CirugiaRepository cirugias;
    private final SolicitudCirugiaRepository solicitudes;
    private final QuirofanoRepository quirofanos;
    private final JdbcClient jdbc;
    private final Clock clock;

    public ProgramacionCirugiasService(CirugiaRepository cirugias, SolicitudCirugiaRepository solicitudes,
            QuirofanoRepository quirofanos, JdbcClient jdbc, Clock clock) {
        this.cirugias = cirugias;
        this.solicitudes = solicitudes;
        this.quirofanos = quirofanos;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public ProgramacionCirugiaResponse programar(ProgramarCirugiaRequest datos) {
        SolicitudCirugia solicitud = solicitudes.findById(datos.solicitudCirugiaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Solicitud de cirugía no encontrada: " + datos.solicitudCirugiaId()));
        if (solicitud.getEstado() != EstadoSolicitudCirugia.APROBADA) {
            throw new ReglaNegocioException("Solo se programan solicitudes APROBADAS (estado actual: "
                    + solicitud.getEstado() + ")");
        }
        exigirQuirofanoActivo(datos.quirofanoId());

        LocalDateTime fin = datos.finProgramado();
        if (fin == null) {
            Integer duracion = jdbc.sql("SELECT duracion_estimada_minutos FROM procedimientos_quirurgicos WHERE id = :id")
                    .param("id", solicitud.getProcedimientoId())
                    .query((rs, n) -> entero(rs, "duracion_estimada_minutos"))
                    .optional().orElse(null);
            if (duracion == null) {
                throw new ReglaNegocioException(
                        "El procedimiento no tiene duración estimada: indique finProgramado");
            }
            fin = datos.inicioProgramado().plusMinutes(duracion);
        }

        Cirugia cirugia = new Cirugia();
        cirugia.setSolicitudCirugiaId(solicitud.getId());
        cirugia.setQuirofanoId(datos.quirofanoId());
        cirugia.setProgramadaPorUsuarioId(UsuarioActual.id());
        cirugia.setCoordinadorUsuarioId(datos.coordinadorUsuarioId());
        cirugia.setInicioProgramado(datos.inicioProgramado());
        cirugia.setFinProgramado(fin);
        cirugia.setEstado(EstadoCirugia.PROGRAMADA);
        cirugia.setNotasProgramacion(Textos.limpiar(datos.notasProgramacion()));
        cirugias.saveAndFlush(cirugia);

        solicitud.setEstado(EstadoSolicitudCirugia.PROGRAMADA);
        solicitudes.saveAndFlush(solicitud);
        return detalle(cirugia.getId());
    }

    @Transactional
    public ProgramacionCirugiaResponse reprogramar(UUID id, ReprogramarCirugiaRequest datos) {
        Cirugia cirugia = buscar(id);
        if (!TransicionesCirugia.REPROGRAMABLES.contains(cirugia.getEstado())) {
            throw new ReglaNegocioException("Una cirugía en estado " + cirugia.getEstado() + " no se reprograma");
        }
        if (!datos.quirofanoId().equals(cirugia.getQuirofanoId())) {
            exigirQuirofanoActivo(datos.quirofanoId());
        }
        cirugia.setQuirofanoId(datos.quirofanoId());
        cirugia.setInicioProgramado(datos.inicioProgramado());
        cirugia.setFinProgramado(datos.finProgramado());
        cirugia.setCoordinadorUsuarioId(datos.coordinadorUsuarioId());
        cirugia.setNotasProgramacion(Textos.limpiar(datos.notasProgramacion()));
        cirugias.saveAndFlush(cirugia);
        return detalle(id);
    }

    @Transactional
    public ProgramacionCirugiaResponse cambiarEstado(UUID id, CambioEstadoCirugiaRequest datos) {
        Cirugia cirugia = buscar(id);
        TransicionesCirugia.validar(cirugia.getEstado(), datos.estado());
        if (TransicionesCirugia.esTerminal(datos.estado())) {
            String motivo = Textos.limpiar(datos.motivo());
            if (motivo == null) {
                throw new ReglaNegocioException("El motivo es obligatorio para " + datos.estado());
            }
            String marca = "[" + datos.estado() + " " + LocalDateTime.now(clock).format(MARCA) + "] " + motivo;
            String notas = cirugia.getNotasProgramacion();
            cirugia.setNotasProgramacion(notas == null ? marca : notas + "\n" + marca);
        }
        cirugia.setEstado(datos.estado());
        cirugias.saveAndFlush(cirugia);
        return detalle(id);
    }

    @Transactional(readOnly = true)
    public ProgramacionCirugiaResponse detalle(UUID id) {
        return jdbc.sql("""
                        SELECT c.id, c.solicitud_cirugia_id, c.estado::text AS estado,
                               c.inicio_programado, c.fin_programado, c.notas_programacion,
                               c.creado_en, c.actualizado_en,
                               p.id AS paciente_id, p.nombres || ' ' || p.apellidos AS paciente,
                               pr.id AS procedimiento_id, pr.codigo AS procedimiento_codigo,
                               pr.nombre AS procedimiento_nombre,
                               s.sitio_quirurgico, s.lateralidad::text AS lateralidad,
                               q.id AS quirofano_id, q.codigo AS quirofano_codigo, q.nombre AS quirofano_nombre,
                               up.id AS programada_por_id, up.nombres || ' ' || up.apellidos AS programada_por,
                               uc.id AS coordinador_id, uc.nombres || ' ' || uc.apellidos AS coordinador,
                               NOT EXISTS (
                                   SELECT 1
                                   FROM requerimientos_roles_solicitud r
                                   WHERE r.solicitud_cirugia_id = c.solicitud_cirugia_id
                                     AND r.es_requerido
                                     AND r.cantidad > (SELECT count(*)
                                                       FROM asignaciones_personal_cirugia a
                                                       WHERE a.requerimiento_rol_id = r.id
                                                         AND a.cirugia_id = c.id
                                                         AND a.estado IN ('ASIGNADA', 'CONFIRMADA'))
                               ) AS equipo_completo
                        FROM cirugias c
                                 JOIN solicitudes_cirugia s ON s.id = c.solicitud_cirugia_id
                                 JOIN pacientes p ON p.id = s.paciente_id
                                 JOIN procedimientos_quirurgicos pr ON pr.id = s.procedimiento_id
                                 JOIN quirofanos q ON q.id = c.quirofano_id
                                 JOIN usuarios up ON up.id = c.programada_por_usuario_id
                                 LEFT JOIN usuarios uc ON uc.id = c.coordinador_usuario_id
                        WHERE c.id = :id
                        """)
                .param("id", id)
                .query((rs, n) -> new ProgramacionCirugiaResponse(
                        uuid(rs, "id"),
                        uuid(rs, "solicitud_cirugia_id"),
                        enumeracion(rs, "estado", EstadoCirugia.class),
                        new ProgramacionCirugiaResponse.Persona(uuid(rs, "paciente_id"), rs.getString("paciente")),
                        new ProcedimientoVista(uuid(rs, "procedimiento_id"), rs.getString("procedimiento_codigo"),
                                rs.getString("procedimiento_nombre"), rs.getString("sitio_quirurgico"),
                                enumeracion(rs, "lateralidad", Lateralidad.class)),
                        new QuirofanoVista(uuid(rs, "quirofano_id"), rs.getString("quirofano_codigo"),
                                rs.getString("quirofano_nombre")),
                        fechaHora(rs, "inicio_programado"),
                        fechaHora(rs, "fin_programado"),
                        new ProgramacionCirugiaResponse.Persona(uuid(rs, "programada_por_id"),
                                rs.getString("programada_por")),
                        uuid(rs, "coordinador_id") == null ? null
                                : new ProgramacionCirugiaResponse.Persona(uuid(rs, "coordinador_id"),
                                        rs.getString("coordinador")),
                        rs.getString("notas_programacion"),
                        rs.getBoolean("equipo_completo"),
                        fechaHora(rs, "creado_en"),
                        fechaHora(rs, "actualizado_en")))
                .optional()
                .orElseThrow(() -> new RecursoNoEncontradoException("Cirugía no encontrada: " + id));
    }

    Cirugia buscar(UUID id) {
        return cirugias.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cirugía no encontrada: " + id));
    }

    private void exigirQuirofanoActivo(UUID quirofanoId) {
        Quirofano quirofano = quirofanos.findById(quirofanoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Quirófano no encontrado: " + quirofanoId));
        if (!quirofano.isActivo()) {
            throw new ReglaNegocioException("El quirófano " + quirofano.getCodigo() + " está inactivo");
        }
    }
}
