package com.hackaton.ulibre.cirugias;

import static com.hackaton.ulibre.comun.Filas.enumeracion;
import static com.hackaton.ulibre.comun.Filas.fechaHora;
import static com.hackaton.ulibre.comun.Filas.uuid;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.hackaton.ulibre.catalogos.RolClinicoVista;
import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.ReglaNegocioException;
import com.hackaton.ulibre.comun.Textos;
import com.hackaton.ulibre.comun.UsuarioActual;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asignación de personal. Rol clínico, especialidad, cupo, solapes y NO_DISPONIBLE los valida el
 * trigger tg_asignaciones_validar; un único operador vigente, el índice ux_cirugia_operador_tablero.
 */
@Service
public class AsignacionesService {

    private static final Set<EstadoAsignacion> VIGENTES = EnumSet.of(EstadoAsignacion.ASIGNADA,
            EstadoAsignacion.CONFIRMADA);
    private static final Set<EstadoCirugia> SIN_ASIGNACIONES = EnumSet.of(EstadoCirugia.CANCELADA,
            EstadoCirugia.SUSPENDIDA, EstadoCirugia.COMPLETADA);

    private final AsignacionPersonalCirugiaRepository asignaciones;
    private final CirugiaRepository cirugias;
    private final JdbcClient jdbc;
    private final Clock clock;

    public AsignacionesService(AsignacionPersonalCirugiaRepository asignaciones, CirugiaRepository cirugias,
            JdbcClient jdbc, Clock clock) {
        this.asignaciones = asignaciones;
        this.cirugias = cirugias;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Todas, también las rechazadas y canceladas (son histórico de la cirugía). */
    @Transactional(readOnly = true)
    public List<AsignacionResponse> listar(UUID cirugiaId) {
        buscarCirugia(cirugiaId);
        return consulta("a.cirugia_id = :cirugiaId", "cirugiaId", cirugiaId);
    }

    @Transactional
    public AsignacionResponse asignar(UUID cirugiaId, AsignacionRequest datos) {
        Cirugia cirugia = buscarCirugia(cirugiaId);
        if (SIN_ASIGNACIONES.contains(cirugia.getEstado())) {
            throw new ReglaNegocioException("No se asigna personal a una cirugía " + cirugia.getEstado());
        }

        // Rechazada o cancelada se reactiva (UNIQUE cirugia + profesional); vigente → el UNIQUE responde 409
        AsignacionPersonalCirugia asignacion = asignaciones
                .findByCirugiaIdAndProfesionalId(cirugiaId, datos.profesionalId())
                .filter(a -> !VIGENTES.contains(a.getEstado()))
                .orElseGet(AsignacionPersonalCirugia::new);
        asignacion.setCirugiaId(cirugiaId);
        asignacion.setSolicitudCirugiaId(cirugia.getSolicitudCirugiaId());
        asignacion.setRequerimientoRolId(datos.requerimientoRolId());
        asignacion.setProfesionalId(datos.profesionalId());
        asignacion.setEstado(EstadoAsignacion.ASIGNADA);
        asignacion.setEsOperadorTablero(Boolean.TRUE.equals(datos.esOperadorTablero()));
        asignacion.setAsignadoPorUsuarioId(UsuarioActual.id());
        asignacion.setAsignadoEn(LocalDateTime.now(clock));
        asignacion.setConfirmadoEn(null);
        asignacion.setNotas(Textos.limpiar(datos.notas()));
        asignaciones.saveAndFlush(asignacion);
        return obtener(asignacion.getId());
    }

    @Transactional
    public AsignacionResponse cambiarEstado(UUID cirugiaId, UUID asignacionId, EstadoAsignacion nuevo) {
        AsignacionPersonalCirugia asignacion = buscar(cirugiaId, asignacionId);
        if (!VIGENTES.contains(asignacion.getEstado()) || nuevo == EstadoAsignacion.ASIGNADA
                || nuevo == asignacion.getEstado()) {
            throw new ReglaNegocioException("Cambio de estado no permitido: " + asignacion.getEstado() + " → "
                    + nuevo + " (para reactivar, vuelva a asignar al profesional)");
        }
        asignacion.setEstado(nuevo);
        if (nuevo == EstadoAsignacion.CONFIRMADA) {
            asignacion.setConfirmadoEn(LocalDateTime.now(clock));
        } else {
            asignacion.setEsOperadorTablero(false);
        }
        asignaciones.saveAndFlush(asignacion);
        return obtener(asignacionId);
    }

    @Transactional
    public AsignacionResponse designarOperador(UUID cirugiaId, UUID asignacionId) {
        AsignacionPersonalCirugia nueva = buscar(cirugiaId, asignacionId);
        if (!VIGENTES.contains(nueva.getEstado())) {
            throw new ReglaNegocioException("Solo una asignación vigente puede operar el tablero");
        }
        // Primero se libera al operador actual: el índice único no admite dos vigentes a la vez
        operadorVigente(cirugiaId).filter(a -> !a.getId().equals(asignacionId)).ifPresent(actual -> {
            actual.setEsOperadorTablero(false);
            asignaciones.saveAndFlush(actual);
        });
        nueva.setEsOperadorTablero(true);
        asignaciones.saveAndFlush(nueva);
        return obtener(asignacionId);
    }

    @Transactional
    public void quitarOperador(UUID cirugiaId) {
        buscarCirugia(cirugiaId);
        operadorVigente(cirugiaId).ifPresent(actual -> {
            actual.setEsOperadorTablero(false);
            asignaciones.saveAndFlush(actual);
        });
    }

    private Optional<AsignacionPersonalCirugia> operadorVigente(UUID cirugiaId) {
        return asignaciones.findByCirugiaIdAndEsOperadorTableroTrueAndEstadoIn(cirugiaId, VIGENTES);
    }

    private AsignacionResponse obtener(UUID asignacionId) {
        return consulta("a.id = :id", "id", asignacionId).getFirst();
    }

    private List<AsignacionResponse> consulta(String filtro, String parametro, UUID valor) {
        return jdbc.sql("""
                        SELECT a.id, a.requerimiento_rol_id, rc.codigo AS rol_codigo, rc.nombre AS rol_nombre,
                               a.profesional_id, u.nombres || ' ' || u.apellidos AS profesional,
                               a.estado::text AS estado, a.es_operador_tablero,
                               ua.nombres || ' ' || ua.apellidos AS asignado_por,
                               a.asignado_en, a.confirmado_en, a.notas
                        FROM asignaciones_personal_cirugia a
                                 JOIN requerimientos_roles_solicitud r ON r.id = a.requerimiento_rol_id
                                 JOIN roles_clinicos rc ON rc.id = r.rol_clinico_id
                                 JOIN perfiles_profesionales pp ON pp.id = a.profesional_id
                                 JOIN usuarios u ON u.id = pp.usuario_id
                                 JOIN usuarios ua ON ua.id = a.asignado_por_usuario_id
                        WHERE %s
                        ORDER BY rc.nombre, u.apellidos, u.nombres
                        """.formatted(filtro))
                .param(parametro, valor)
                .query((rs, n) -> new AsignacionResponse(
                        uuid(rs, "id"),
                        uuid(rs, "requerimiento_rol_id"),
                        RolClinicoVista.de(rs, "rol_codigo", "rol_nombre"),
                        uuid(rs, "profesional_id"),
                        rs.getString("profesional"),
                        enumeracion(rs, "estado", EstadoAsignacion.class),
                        rs.getBoolean("es_operador_tablero"),
                        rs.getString("asignado_por"),
                        fechaHora(rs, "asignado_en"),
                        fechaHora(rs, "confirmado_en"),
                        rs.getString("notas")))
                .list();
    }

    private AsignacionPersonalCirugia buscar(UUID cirugiaId, UUID asignacionId) {
        return asignaciones.findByIdAndCirugiaId(asignacionId, cirugiaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Asignación no encontrada: " + asignacionId));
    }

    private Cirugia buscarCirugia(UUID cirugiaId) {
        return cirugias.findById(cirugiaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cirugía no encontrada: " + cirugiaId));
    }
}
