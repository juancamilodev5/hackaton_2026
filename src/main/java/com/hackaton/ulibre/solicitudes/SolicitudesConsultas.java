package com.hackaton.ulibre.solicitudes;

import static com.hackaton.ulibre.comun.Filas.enumeracion;
import static com.hackaton.ulibre.comun.Filas.fechaHora;
import static com.hackaton.ulibre.comun.Filas.uuid;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.Lateralidad;
import com.hackaton.ulibre.comun.Pagina;
import com.hackaton.ulibre.comun.Paginacion;
import com.hackaton.ulibre.solicitudes.SolicitudCirugiaResponse.Medico;
import com.hackaton.ulibre.solicitudes.SolicitudCirugiaResponse.Paciente;
import com.hackaton.ulibre.solicitudes.SolicitudCirugiaResponse.Referencia;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Lecturas de solicitudes con SQL nativo: nombres de paciente, médico, especialidad y procedimiento. */
@Component
public class SolicitudesConsultas {

    private static final String SELECT = """
            SELECT s.id, s.estado::text AS estado, s.cita_origen_id, s.sitio_quirurgico,
                   s.lateralidad::text AS lateralidad, s.resumen_clinico, s.notas_medicas,
                   s.creado_en, s.enviada_en, s.actualizado_en,
                   p.id AS paciente_id, p.nombres || ' ' || p.apellidos AS paciente_nombre,
                   p.tipo_documento, p.numero_documento,
                   pp.id AS medico_id, u.nombres || ' ' || u.apellidos AS medico_nombre,
                   e.id AS especialidad_id, e.codigo AS especialidad_codigo, e.nombre AS especialidad_nombre,
                   pr.id AS procedimiento_id, pr.codigo AS procedimiento_codigo, pr.nombre AS procedimiento_nombre,
                   c.id AS cirugia_id
            FROM solicitudes_cirugia s
                     JOIN pacientes p ON p.id = s.paciente_id
                     JOIN perfiles_profesionales pp ON pp.id = s.medico_solicitante_id
                     JOIN usuarios u ON u.id = pp.usuario_id
                     JOIN especialidades e ON e.id = s.especialidad_solicitante_id
                     JOIN procedimientos_quirurgicos pr ON pr.id = s.procedimiento_id
                     LEFT JOIN cirugias c ON c.solicitud_cirugia_id = s.id
            """;

    private static final String FILTROS = """
            WHERE (CAST(:estado AS text) IS NULL OR s.estado::text = :estado)
              AND (CAST(:pacienteId AS uuid) IS NULL OR s.paciente_id = :pacienteId)
              AND (CAST(:medicoId AS uuid) IS NULL OR s.medico_solicitante_id = :medicoId)
              AND (CAST(:procedimientoId AS uuid) IS NULL OR s.procedimiento_id = :procedimientoId)
            """;

    private final JdbcClient jdbc;

    public SolicitudesConsultas(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Más recientes primero. */
    public Pagina<SolicitudCirugiaResumen> listar(EstadoSolicitudCirugia estado, UUID pacienteId, UUID medicoId,
            UUID procedimientoId, Paginacion paginacion) {
        String estadoTexto = estado == null ? null : estado.name();
        long total = jdbc.sql("SELECT count(*) FROM solicitudes_cirugia s " + FILTROS)
                .param("estado", estadoTexto)
                .param("pacienteId", pacienteId)
                .param("medicoId", medicoId)
                .param("procedimientoId", procedimientoId)
                .query(Long.class)
                .single();
        List<SolicitudCirugiaResumen> contenido = jdbc.sql(SELECT + FILTROS + """
                        ORDER BY s.creado_en DESC, s.id DESC
                        LIMIT :limite OFFSET :desplazamiento
                        """)
                .param("estado", estadoTexto)
                .param("pacienteId", pacienteId)
                .param("medicoId", medicoId)
                .param("procedimientoId", procedimientoId)
                .param("limite", paginacion.tamano())
                .param("desplazamiento", paginacion.desplazamiento())
                .query((rs, n) -> new SolicitudCirugiaResumen(
                        uuid(rs, "id"),
                        enumeracion(rs, "estado", EstadoSolicitudCirugia.class),
                        paciente(rs), medico(rs), especialidad(rs), procedimiento(rs),
                        rs.getString("sitio_quirurgico"),
                        enumeracion(rs, "lateralidad", Lateralidad.class),
                        fechaHora(rs, "creado_en"),
                        fechaHora(rs, "enviada_en"),
                        uuid(rs, "cirugia_id")))
                .list();
        return Pagina.de(contenido, paginacion, total);
    }

    public Optional<SolicitudCirugiaResponse> detalle(UUID id) {
        return jdbc.sql(SELECT + "WHERE s.id = :id")
                .param("id", id)
                .query((rs, n) -> new SolicitudCirugiaResponse(
                        uuid(rs, "id"),
                        enumeracion(rs, "estado", EstadoSolicitudCirugia.class),
                        paciente(rs), medico(rs), especialidad(rs), procedimiento(rs),
                        uuid(rs, "cita_origen_id"),
                        rs.getString("sitio_quirurgico"),
                        enumeracion(rs, "lateralidad", Lateralidad.class),
                        rs.getString("resumen_clinico"),
                        rs.getString("notas_medicas"),
                        fechaHora(rs, "creado_en"),
                        fechaHora(rs, "enviada_en"),
                        fechaHora(rs, "actualizado_en"),
                        uuid(rs, "cirugia_id"),
                        List.of()))
                .optional()
                .map(s -> new SolicitudCirugiaResponse(s.id(), s.estado(), s.paciente(), s.medico(),
                        s.especialidad(), s.procedimiento(), s.citaOrigenId(), s.sitioQuirurgico(), s.lateralidad(),
                        s.resumenClinico(), s.notasMedicas(), s.creadoEn(), s.enviadaEn(), s.actualizadoEn(),
                        s.cirugiaId(), requerimientos(id)));
    }

    /** En orden de creación, con cuántas asignaciones vigentes cubren cada uno. */
    public List<RequerimientoResponse> requerimientos(UUID solicitudId) {
        return jdbc.sql("""
                        SELECT r.id, r.cantidad, r.es_requerido, r.notas,
                               rc.id AS rol_id, rc.codigo AS rol_codigo, rc.nombre AS rol_nombre,
                               e.id AS especialidad_id, e.codigo AS especialidad_codigo,
                               e.nombre AS especialidad_nombre,
                               (SELECT count(*)
                                FROM asignaciones_personal_cirugia a
                                WHERE a.requerimiento_rol_id = r.id
                                  AND a.estado IN ('ASIGNADA', 'CONFIRMADA')) AS asignaciones_vigentes
                        FROM requerimientos_roles_solicitud r
                                 JOIN roles_clinicos rc ON rc.id = r.rol_clinico_id
                                 LEFT JOIN especialidades e ON e.id = r.especialidad_id
                        WHERE r.solicitud_cirugia_id = :solicitudId
                        ORDER BY r.creado_en, r.id
                        """)
                .param("solicitudId", solicitudId)
                .query((rs, n) -> new RequerimientoResponse(
                        uuid(rs, "id"),
                        new Referencia(uuid(rs, "rol_id"), rs.getString("rol_codigo"), rs.getString("rol_nombre")),
                        especialidad(rs),
                        rs.getInt("cantidad"),
                        rs.getBoolean("es_requerido"),
                        rs.getString("notas"),
                        rs.getInt("asignaciones_vigentes")))
                .list();
    }

    /** Perfil profesional ACTIVO del usuario (para "el médico que solicita soy yo"). */
    public Optional<UUID> perfilProfesionalActivo(UUID usuarioId) {
        return jdbc.sql("SELECT id FROM perfiles_profesionales WHERE usuario_id = :usuarioId AND activo")
                .param("usuarioId", usuarioId)
                .query(UUID.class)
                .optional();
    }

    /** null si el procedimiento no existe. */
    public Boolean procedimientoActivo(UUID procedimientoId) {
        return jdbc.sql("SELECT activo FROM procedimientos_quirurgicos WHERE id = :id")
                .param("id", procedimientoId)
                .query(Boolean.class)
                .optional()
                .orElse(null);
    }

    /** Paciente de la cita, o vacío si la cita no existe. */
    public Optional<UUID> pacienteDeCita(UUID citaId) {
        return jdbc.sql("SELECT paciente_id FROM citas WHERE id = :id")
                .param("id", citaId)
                .query(UUID.class)
                .optional();
    }

    /** Roles sugeridos del procedimiento → requerimientos de la solicitud. Devuelve cuántos copió. */
    public int copiarRolesPredeterminados(UUID solicitudId, UUID procedimientoId) {
        return jdbc.sql("""
                        INSERT INTO requerimientos_roles_solicitud
                            (solicitud_cirugia_id, rol_clinico_id, especialidad_id, cantidad, es_requerido, notas)
                        SELECT :solicitudId, rp.rol_clinico_id, rp.especialidad_id, rp.cantidad_predeterminada,
                               rp.es_requerido, rp.notas
                        FROM roles_predeterminados_procedimiento rp
                        WHERE rp.procedimiento_id = :procedimientoId
                        ORDER BY rp.creado_en, rp.id
                        """)
                .param("solicitudId", solicitudId)
                .param("procedimientoId", procedimientoId)
                .update();
    }

    public int asignacionesVigentes(UUID requerimientoId) {
        return jdbc.sql("""
                        SELECT count(*) FROM asignaciones_personal_cirugia
                        WHERE requerimiento_rol_id = :id AND estado IN ('ASIGNADA', 'CONFIRMADA')
                        """)
                .param("id", requerimientoId)
                .query(Integer.class)
                .single();
    }

    private static Paciente paciente(ResultSet rs) throws SQLException {
        return new Paciente(uuid(rs, "paciente_id"), rs.getString("paciente_nombre"),
                rs.getString("tipo_documento"), rs.getString("numero_documento"));
    }

    private static Medico medico(ResultSet rs) throws SQLException {
        return new Medico(uuid(rs, "medico_id"), rs.getString("medico_nombre"));
    }

    /** null si la columna viene vacía (requerimiento sin especialidad exigida). */
    private static Referencia especialidad(ResultSet rs) throws SQLException {
        UUID id = uuid(rs, "especialidad_id");
        return id == null ? null
                : new Referencia(id, rs.getString("especialidad_codigo"), rs.getString("especialidad_nombre"));
    }

    private static Referencia procedimiento(ResultSet rs) throws SQLException {
        return new Referencia(uuid(rs, "procedimiento_id"), rs.getString("procedimiento_codigo"),
                rs.getString("procedimiento_nombre"));
    }
}
