package com.hackaton.ulibre.cirugias;

import static com.hackaton.ulibre.comun.Filas.enumeracion;
import static com.hackaton.ulibre.comun.Filas.fecha;
import static com.hackaton.ulibre.comun.Filas.fechaHora;
import static com.hackaton.ulibre.comun.Filas.uuid;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.hackaton.ulibre.catalogos.QuirofanoVista;
import com.hackaton.ulibre.catalogos.RolClinicoVista;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Lecturas de cirugías con SQL nativo (sin grafos de entidades). */
@Component
public class CirugiasConsultas {

    private final JdbcClient jdbc;
    private final Clock clock;

    public CirugiasConsultas(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Cirugías cuyo inicio programado cae en la fecha dada (hora local), en todos los estados. */
    public List<CirugiaResumen> listar(LocalDate fecha) {
        return jdbc.sql("""
                        SELECT c.id, c.estado::text AS estado, c.inicio_programado, c.fin_programado,
                               p.nombres || ' ' || p.apellidos AS paciente,
                               pr.id AS procedimiento_id, pr.codigo AS procedimiento_codigo,
                               pr.nombre AS procedimiento_nombre,
                               s.sitio_quirurgico, s.lateralidad::text AS lateralidad,
                               q.id AS quirofano_id, q.codigo AS quirofano_codigo, q.nombre AS quirofano_nombre,
                               (SELECT count(*)
                                FROM alertas al
                                WHERE al.cirugia_id = c.id
                                  AND al.bloqueante
                                  AND al.estado IN ('ABIERTA', 'RECONOCIDA')
                                  AND al.excepcion_autorizada_por_usuario_id IS NULL) AS alertas_bloqueantes,
                               (SELECT u.nombres || ' ' || u.apellidos
                                FROM asignaciones_personal_cirugia a
                                         JOIN perfiles_profesionales pp ON pp.id = a.profesional_id
                                         JOIN usuarios u ON u.id = pp.usuario_id
                                WHERE a.cirugia_id = c.id
                                  AND a.es_operador_tablero
                                  AND a.estado IN ('ASIGNADA', 'CONFIRMADA')) AS operador_tablero,
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
                        WHERE c.inicio_programado >= :desde
                          AND c.inicio_programado < :hasta
                        ORDER BY c.inicio_programado, q.codigo
                        """)
                .param("desde", fecha.atStartOfDay())
                .param("hasta", fecha.plusDays(1).atStartOfDay())
                .query((rs, n) -> new CirugiaResumen(
                        uuid(rs, "id"),
                        rs.getString("paciente"),
                        new ProcedimientoVista(uuid(rs, "procedimiento_id"), rs.getString("procedimiento_codigo"),
                                rs.getString("procedimiento_nombre"), rs.getString("sitio_quirurgico"),
                                enumeracion(rs, "lateralidad", Lateralidad.class)),
                        new QuirofanoVista(uuid(rs, "quirofano_id"), rs.getString("quirofano_codigo"),
                                rs.getString("quirofano_nombre")),
                        fechaHora(rs, "inicio_programado"),
                        fechaHora(rs, "fin_programado"),
                        enumeracion(rs, "estado", EstadoCirugia.class),
                        rs.getInt("alertas_bloqueantes"),
                        rs.getString("operador_tablero"),
                        rs.getBoolean("equipo_completo")))
                .list();
    }

    public Optional<CabeceraCirugia> cabecera(UUID cirugiaId) {
        Optional<CabeceraCirugia> cabecera = jdbc.sql("""
                        SELECT c.id, c.estado::text AS estado, c.inicio_programado, c.fin_programado,
                               q.id AS quirofano_id, q.codigo AS quirofano_codigo, q.nombre AS quirofano_nombre,
                               p.id AS paciente_id, p.nombres || ' ' || p.apellidos AS paciente_nombre,
                               p.tipo_documento, p.numero_documento, p.fecha_nacimiento,
                               pr.id AS procedimiento_id, pr.codigo AS procedimiento_codigo,
                               pr.nombre AS procedimiento_nombre,
                               s.sitio_quirurgico, s.lateralidad::text AS lateralidad,
                               d.id AS preoperatorio_id, d.peso_kg, d.talla_cm, d.glucometria_mg_dl,
                               d.requiere_reserva_sangre, d.estado_reserva_sangre::text AS estado_reserva_sangre,
                               d.informacion_clinica_relevante,
                               vu.nombres || ' ' || vu.apellidos AS validado_por, d.validado_en
                        FROM cirugias c
                                 JOIN solicitudes_cirugia s ON s.id = c.solicitud_cirugia_id
                                 JOIN pacientes p ON p.id = s.paciente_id
                                 JOIN procedimientos_quirurgicos pr ON pr.id = s.procedimiento_id
                                 JOIN quirofanos q ON q.id = c.quirofano_id
                                 LEFT JOIN datos_preoperatorios_cirugia d ON d.cirugia_id = c.id
                                 LEFT JOIN usuarios vu ON vu.id = d.validado_por_usuario_id
                        WHERE c.id = :cirugiaId
                        """)
                .param("cirugiaId", cirugiaId)
                .query((rs, n) -> {
                    LocalDate nacimiento = fecha(rs, "fecha_nacimiento");
                    PreoperatorioVista preoperatorio = uuid(rs, "preoperatorio_id") == null ? null
                            : new PreoperatorioVista(
                                    rs.getBigDecimal("peso_kg"),
                                    rs.getBigDecimal("talla_cm"),
                                    rs.getBigDecimal("glucometria_mg_dl"),
                                    rs.getBoolean("requiere_reserva_sangre"),
                                    enumeracion(rs, "estado_reserva_sangre", EstadoReservaSangre.class),
                                    List.of(),
                                    rs.getString("informacion_clinica_relevante"),
                                    rs.getString("validado_por"),
                                    fechaHora(rs, "validado_en"));
                    return new CabeceraCirugia(
                            new CirugiaVista(uuid(rs, "id"), enumeracion(rs, "estado", EstadoCirugia.class),
                                    new QuirofanoVista(uuid(rs, "quirofano_id"), rs.getString("quirofano_codigo"),
                                            rs.getString("quirofano_nombre")),
                                    fechaHora(rs, "inicio_programado"), fechaHora(rs, "fin_programado")),
                            new PacienteVista(uuid(rs, "paciente_id"), rs.getString("paciente_nombre"),
                                    rs.getString("tipo_documento"), rs.getString("numero_documento"),
                                    nacimiento, edad(nacimiento)),
                            new ProcedimientoVista(uuid(rs, "procedimiento_id"), rs.getString("procedimiento_codigo"),
                                    rs.getString("procedimiento_nombre"), rs.getString("sitio_quirurgico"),
                                    enumeracion(rs, "lateralidad", Lateralidad.class)),
                            preoperatorio);
                })
                .optional();

        return cabecera.map(c -> c.preoperatorio() == null ? c
                : new CabeceraCirugia(c.cirugia(), c.paciente(), c.procedimiento(),
                        conAlergias(c.preoperatorio(), alergias(cirugiaId))));
    }

    /** Requerimientos de la solicitud con sus asignaciones vigentes, en orden de creación. */
    public EquipoVista equipo(UUID cirugiaId) {
        record Fila(UUID requerimientoId, RolClinicoVista rol, String especialidad, int cantidad,
                    boolean esRequerido, AsignacionVista asignacion) {
        }
        List<Fila> filas = jdbc.sql("""
                        SELECT r.id AS requerimiento_id, rc.codigo AS rol_codigo, rc.nombre AS rol_nombre,
                               e.nombre AS especialidad, r.cantidad, r.es_requerido,
                               a.id AS asignacion_id, a.profesional_id,
                               u.nombres || ' ' || u.apellidos AS profesional,
                               a.estado::text AS asignacion_estado, a.es_operador_tablero
                        FROM cirugias c
                                 JOIN requerimientos_roles_solicitud r ON r.solicitud_cirugia_id = c.solicitud_cirugia_id
                                 JOIN roles_clinicos rc ON rc.id = r.rol_clinico_id
                                 LEFT JOIN especialidades e ON e.id = r.especialidad_id
                                 LEFT JOIN asignaciones_personal_cirugia a
                                           ON a.requerimiento_rol_id = r.id
                                               AND a.cirugia_id = c.id
                                               AND a.estado IN ('ASIGNADA', 'CONFIRMADA')
                                 LEFT JOIN perfiles_profesionales pp ON pp.id = a.profesional_id
                                 LEFT JOIN usuarios u ON u.id = pp.usuario_id
                        WHERE c.id = :cirugiaId
                        ORDER BY r.id, u.apellidos, u.nombres
                        """)
                .param("cirugiaId", cirugiaId)
                .query((rs, n) -> new Fila(
                        uuid(rs, "requerimiento_id"),
                        RolClinicoVista.de(rs, "rol_codigo", "rol_nombre"),
                        rs.getString("especialidad"),
                        rs.getInt("cantidad"),
                        rs.getBoolean("es_requerido"),
                        uuid(rs, "asignacion_id") == null ? null
                                : new AsignacionVista(uuid(rs, "asignacion_id"), uuid(rs, "profesional_id"),
                                        rs.getString("profesional"),
                                        enumeracion(rs, "asignacion_estado", EstadoAsignacion.class),
                                        rs.getBoolean("es_operador_tablero"))))
                .list();

        Map<UUID, List<Fila>> porRequerimiento = new LinkedHashMap<>();
        for (Fila fila : filas) {
            porRequerimiento.computeIfAbsent(fila.requerimientoId(), k -> new ArrayList<>()).add(fila);
        }
        List<RequerimientoVista> requerimientos = porRequerimiento.values().stream()
                .map(grupo -> {
                    Fila primera = grupo.getFirst();
                    List<AsignacionVista> asignaciones = grupo.stream()
                            .map(Fila::asignacion)
                            .filter(a -> a != null)
                            .toList();
                    return new RequerimientoVista(primera.requerimientoId(), primera.rol(), primera.especialidad(),
                            primera.cantidad(), primera.esRequerido(), asignaciones.size(), asignaciones);
                })
                .toList();
        boolean completo = requerimientos.stream()
                .filter(RequerimientoVista::esRequerido)
                .allMatch(RequerimientoVista::cubierto);
        return new EquipoVista(completo, requerimientos);
    }

    /**
     * Todas las asignaciones de la cirugía (también las ya canceladas: firmaron registros
     * históricos), por id, para resolver quién registró, confirmó o cerró algo.
     */
    public Map<UUID, ParticipanteVista> participantes(UUID cirugiaId) {
        Map<UUID, ParticipanteVista> participantes = new HashMap<>();
        jdbc.sql("""
                        SELECT a.id, u.nombres || ' ' || u.apellidos AS nombre,
                               rc.codigo AS rol_codigo, rc.nombre AS rol_nombre
                        FROM asignaciones_personal_cirugia a
                                 JOIN perfiles_profesionales pp ON pp.id = a.profesional_id
                                 JOIN usuarios u ON u.id = pp.usuario_id
                                 JOIN requerimientos_roles_solicitud r ON r.id = a.requerimiento_rol_id
                                 JOIN roles_clinicos rc ON rc.id = r.rol_clinico_id
                        WHERE a.cirugia_id = :cirugiaId
                        """)
                .param("cirugiaId", cirugiaId)
                .query(rs -> {
                    UUID id = uuid(rs, "id");
                    participantes.put(id, new ParticipanteVista(id, rs.getString("nombre"),
                            RolClinicoVista.de(rs, "rol_codigo", "rol_nombre")));
                });
        return participantes;
    }

    /** copia_alergias es un arreglo JSON; si viene con otra forma se ignora en vez de fallar. */
    private List<AlergiaVista> alergias(UUID cirugiaId) {
        return jdbc.sql("""
                        SELECT e ->> 'sustancia' AS sustancia, e ->> 'reaccion' AS reaccion,
                               e ->> 'severidad' AS severidad
                        FROM datos_preoperatorios_cirugia d
                                 CROSS JOIN LATERAL jsonb_array_elements(
                                     CASE WHEN jsonb_typeof(d.copia_alergias) = 'array'
                                          THEN d.copia_alergias ELSE '[]'::jsonb END) AS e
                        WHERE d.cirugia_id = :cirugiaId
                        """)
                .param("cirugiaId", cirugiaId)
                .query((rs, n) -> new AlergiaVista(rs.getString("sustancia"), rs.getString("reaccion"),
                        rs.getString("severidad")))
                .list();
    }

    private static PreoperatorioVista conAlergias(PreoperatorioVista p, List<AlergiaVista> alergias) {
        return new PreoperatorioVista(p.pesoKg(), p.tallaCm(), p.glucometriaMgDl(), p.requiereReservaSangre(),
                p.estadoReservaSangre(), alergias, p.informacionClinicaRelevante(), p.validadoPor(), p.validadoEn());
    }

    private Integer edad(LocalDate nacimiento) {
        return nacimiento == null ? null : Period.between(nacimiento, LocalDate.now(clock)).getYears();
    }
}
