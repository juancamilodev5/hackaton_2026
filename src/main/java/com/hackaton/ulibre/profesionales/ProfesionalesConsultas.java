package com.hackaton.ulibre.profesionales;

import static com.hackaton.ulibre.comun.Filas.fechaHora;
import static com.hackaton.ulibre.comun.Filas.uuid;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.hackaton.ulibre.comun.Pagina;
import com.hackaton.ulibre.comun.Paginacion;
import com.hackaton.ulibre.comun.Textos;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Lecturas de profesionales con SQL nativo: una consulta para la página de perfiles y una por
 * relación (roles clínicos, especialidades) para todos los perfiles de la página, sin N+1.
 */
@Component
public class ProfesionalesConsultas {

    private final JdbcClient jdbc;

    public ProfesionalesConsultas(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private record Fila(UUID id, UUID usuarioId, String nombres, String apellidos, String correo, String licencia,
                        String numero, boolean activo, LocalDateTime creadoEn, LocalDateTime actualizadoEn) {
    }

    private static final String SELECT = """
            SELECT pp.id, pp.usuario_id, u.nombres, u.apellidos, u.correo, pp.licencia_profesional,
                   pp.numero_profesional, pp.activo, pp.creado_en, pp.actualizado_en
            FROM perfiles_profesionales pp
                     JOIN usuarios u ON u.id = pp.usuario_id
            """;

    public Pagina<ProfesionalResponse> listar(Boolean activo, UUID rolClinicoId, UUID especialidadId, String q,
            Paginacion paginacion) {
        StringBuilder where = new StringBuilder(" WHERE true");
        Map<String, Object> params = new HashMap<>();
        if (activo != null) {
            where.append(" AND pp.activo = :activo");
            params.put("activo", activo);
        }
        if (rolClinicoId != null) {
            where.append(" AND EXISTS (SELECT 1 FROM profesional_roles_clinicos prc"
                    + " WHERE prc.profesional_id = pp.id AND prc.rol_clinico_id = :rolClinicoId)");
            params.put("rolClinicoId", rolClinicoId);
        }
        if (especialidadId != null) {
            where.append(" AND EXISTS (SELECT 1 FROM profesional_especialidades pe"
                    + " WHERE pe.profesional_id = pp.id AND pe.especialidad_id = :especialidadId)");
            params.put("especialidadId", especialidadId);
        }
        if (q != null) {
            where.append(" AND ((u.nombres || ' ' || u.apellidos) ILIKE :q OR u.correo ILIKE :q)");
            params.put("q", Textos.patronLike(q));
        }

        long total = jdbc.sql("SELECT count(*) FROM perfiles_profesionales pp JOIN usuarios u ON u.id = pp.usuario_id"
                        + where)
                .params(params)
                .query(Long.class)
                .single();
        List<Fila> filas = jdbc.sql(SELECT + where
                        + " ORDER BY u.apellidos, u.nombres, pp.id LIMIT :limite OFFSET :desplazamiento")
                .params(params)
                .param("limite", paginacion.tamano())
                .param("desplazamiento", paginacion.desplazamiento())
                .query((rs, n) -> fila(rs))
                .list();
        return Pagina.de(completar(filas), paginacion, total);
    }

    public Optional<ProfesionalResponse> obtener(UUID id) {
        return jdbc.sql(SELECT + " WHERE pp.id = :id")
                .param("id", id)
                .query((rs, n) -> fila(rs))
                .optional()
                .map(f -> completar(List.of(f)).getFirst());
    }

    /**
     * Profesionales activos con el rol (y la especialidad, si se pide) que no tienen un bloque
     * NO_DISPONIBLE ni una asignación vigente en una cirugía vigente que se solape con [desde, hasta).
     * Mismo criterio que fn_validar_asignacion; la base vuelve a validarlo al asignar.
     */
    public List<ProfesionalDisponibleResponse> disponibles(LocalDateTime desde, LocalDateTime hasta, UUID rolClinicoId,
            UUID especialidadId) {
        return jdbc.sql("""
                        SELECT pp.id, pp.usuario_id, u.nombres, u.apellidos,
                               EXISTS (SELECT 1 FROM disponibilidad_profesional d
                                       WHERE d.profesional_id = pp.id AND d.tipo_disponibilidad = 'DE_TURNO'
                                         AND tsrange(d.inicia_en, d.termina_en, '[)') && tsrange(:desde, :hasta, '[)')
                               ) AS de_turno,
                               EXISTS (SELECT 1 FROM disponibilidad_profesional d
                                       WHERE d.profesional_id = pp.id AND d.tipo_disponibilidad = 'DISPONIBLE'
                                         AND tsrange(d.inicia_en, d.termina_en, '[)') && tsrange(:desde, :hasta, '[)')
                               ) AS marcado_disponible
                        FROM perfiles_profesionales pp
                                 JOIN usuarios u ON u.id = pp.usuario_id
                        WHERE pp.activo
                          AND EXISTS (SELECT 1 FROM profesional_roles_clinicos prc
                                      WHERE prc.profesional_id = pp.id AND prc.rol_clinico_id = :rolClinicoId)
                          AND NOT EXISTS (SELECT 1 FROM disponibilidad_profesional d
                                          WHERE d.profesional_id = pp.id AND d.tipo_disponibilidad = 'NO_DISPONIBLE'
                                            AND tsrange(d.inicia_en, d.termina_en, '[)') && tsrange(:desde, :hasta, '[)'))
                          AND NOT EXISTS (SELECT 1
                                          FROM asignaciones_personal_cirugia a
                                                   JOIN cirugias c ON c.id = a.cirugia_id
                                          WHERE a.profesional_id = pp.id
                                            AND a.estado IN ('ASIGNADA', 'CONFIRMADA')
                                            AND c.estado NOT IN ('CANCELADA', 'SUSPENDIDA')
                                            AND tsrange(c.inicio_programado, c.fin_programado, '[)')
                                                && tsrange(:desde, :hasta, '[)'))
                        """ + (especialidadId == null ? "" : """
                          AND EXISTS (SELECT 1 FROM profesional_especialidades pe
                                      WHERE pe.profesional_id = pp.id AND pe.especialidad_id = :especialidadId)
                        """) + " ORDER BY u.apellidos, u.nombres")
                .param("desde", desde)
                .param("hasta", hasta)
                .param("rolClinicoId", rolClinicoId)
                .params(especialidadId == null ? Map.of() : Map.of("especialidadId", especialidadId))
                .query((rs, n) -> new ProfesionalDisponibleResponse(uuid(rs, "id"), uuid(rs, "usuario_id"),
                        rs.getString("nombres"), rs.getString("apellidos"), rs.getBoolean("de_turno"),
                        rs.getBoolean("marcado_disponible")))
                .list();
    }

    private List<ProfesionalResponse> completar(List<Fila> filas) {
        if (filas.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = filas.stream().map(Fila::id).toList();
        Map<UUID, List<Referencia>> roles = relacion("""
                SELECT prc.profesional_id, rc.id, rc.codigo, rc.nombre
                FROM profesional_roles_clinicos prc
                         JOIN roles_clinicos rc ON rc.id = prc.rol_clinico_id
                WHERE prc.profesional_id IN (:ids)
                ORDER BY rc.nombre
                """, ids);
        Map<UUID, List<Referencia>> especialidades = relacion("""
                SELECT pe.profesional_id, e.id, e.codigo, e.nombre
                FROM profesional_especialidades pe
                         JOIN especialidades e ON e.id = pe.especialidad_id
                WHERE pe.profesional_id IN (:ids)
                ORDER BY e.nombre
                """, ids);
        return filas.stream()
                .map(f -> new ProfesionalResponse(f.id(), f.usuarioId(), f.nombres(), f.apellidos(), f.correo(),
                        f.licencia(), f.numero(), f.activo(),
                        roles.getOrDefault(f.id(), List.of()),
                        especialidades.getOrDefault(f.id(), List.of()),
                        f.creadoEn(), f.actualizadoEn()))
                .toList();
    }

    private Map<UUID, List<Referencia>> relacion(String sql, List<UUID> ids) {
        Map<UUID, List<Referencia>> porProfesional = new HashMap<>();
        jdbc.sql(sql)
                .param("ids", ids)
                .query(rs -> {
                    porProfesional.computeIfAbsent(uuid(rs, "profesional_id"), k -> new ArrayList<>())
                            .add(new Referencia(uuid(rs, "id"), rs.getString("codigo"), rs.getString("nombre")));
                });
        return porProfesional;
    }

    private static Fila fila(ResultSet rs) throws SQLException {
        return new Fila(uuid(rs, "id"), uuid(rs, "usuario_id"), rs.getString("nombres"), rs.getString("apellidos"),
                rs.getString("correo"), rs.getString("licencia_profesional"), rs.getString("numero_profesional"),
                rs.getBoolean("activo"), fechaHora(rs, "creado_en"), fechaHora(rs, "actualizado_en"));
    }
}
