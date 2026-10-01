package com.hackaton.ulibre.portal;

import static com.hackaton.ulibre.comun.Filas.enumeracion;
import static com.hackaton.ulibre.comun.Filas.fecha;
import static com.hackaton.ulibre.comun.Filas.fechaHora;
import static com.hackaton.ulibre.comun.Filas.uuid;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.EstadoCirugia;
import com.hackaton.ulibre.cirugias.Lateralidad;
import com.hackaton.ulibre.citas.EstadoCita;
import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.UsuarioActual;
import com.hackaton.ulibre.solicitudes.EstadoSolicitudCirugia;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Lecturas del portal del paciente. El paciente es el registro de pacientes vinculado al usuario del
 * token (pacientes.usuario_id); TODAS las consultas filtran por ese paciente en SQL, nunca por un
 * id del cliente sin comprobar pertenencia.
 */
@Component
public class MisDatosConsultas {

    private static final String SELECT_CIRUGIA = """
            SELECT c.id, c.solicitud_cirugia_id, pr.nombre AS procedimiento, s.sitio_quirurgico,
                   s.lateralidad::text AS lateralidad, q.nombre AS quirofano, c.inicio_programado,
                   c.fin_programado, c.estado::text AS estado
            FROM cirugias c
                     JOIN solicitudes_cirugia s ON s.id = c.solicitud_cirugia_id
                     JOIN procedimientos_quirurgicos pr ON pr.id = s.procedimiento_id
                     JOIN quirofanos q ON q.id = c.quirofano_id
            WHERE s.paciente_id = :pacienteId
            """;

    private final JdbcClient jdbc;
    private final Clock clock;

    public MisDatosConsultas(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** 404 si el usuario autenticado no tiene un registro de paciente vinculado. */
    public UUID pacienteActual() {
        return jdbc.sql("SELECT id FROM pacientes WHERE usuario_id = :usuarioId")
                .param("usuarioId", UsuarioActual.id())
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new RecursoNoEncontradoException("No tiene un registro de paciente vinculado"));
    }

    public MiPacienteResponse paciente(UUID pacienteId) {
        List<MiPacienteResponse.Alergia> alergias = jdbc.sql("""
                        SELECT sustancia, reaccion, severidad
                        FROM alergias_paciente
                        WHERE paciente_id = :pacienteId AND activo
                        ORDER BY sustancia
                        """)
                .param("pacienteId", pacienteId)
                .query((rs, n) -> new MiPacienteResponse.Alergia(rs.getString("sustancia"),
                        rs.getString("reaccion"), rs.getString("severidad")))
                .list();
        return jdbc.sql("""
                        SELECT id, nombres, apellidos, tipo_documento, numero_documento, fecha_nacimiento, telefono,
                               correo, contacto_emergencia_nombre, contacto_emergencia_telefono
                        FROM pacientes
                        WHERE id = :pacienteId
                        """)
                .param("pacienteId", pacienteId)
                .query((rs, n) -> {
                    LocalDate nacimiento = fecha(rs, "fecha_nacimiento");
                    return new MiPacienteResponse(uuid(rs, "id"), rs.getString("nombres"), rs.getString("apellidos"),
                            rs.getString("tipo_documento"), rs.getString("numero_documento"), nacimiento,
                            nacimiento == null ? null : Period.between(nacimiento, LocalDate.now(clock)).getYears(),
                            rs.getString("telefono"), rs.getString("correo"),
                            rs.getString("contacto_emergencia_nombre"), rs.getString("contacto_emergencia_telefono"),
                            alergias);
                })
                .single();
    }

    /** Más recientes primero. */
    public List<MiCitaResponse> citas(UUID pacienteId) {
        return jdbc.sql("""
                        SELECT c.id, c.programada_para, u.nombres || ' ' || u.apellidos AS medico,
                               e.nombre AS especialidad, c.estado::text AS estado, c.motivo
                        FROM citas c
                                 JOIN perfiles_profesionales pp ON pp.id = c.medico_id
                                 JOIN usuarios u ON u.id = pp.usuario_id
                                 JOIN especialidades e ON e.id = c.especialidad_id
                        WHERE c.paciente_id = :pacienteId
                        ORDER BY c.programada_para DESC, c.id
                        """)
                .param("pacienteId", pacienteId)
                .query((rs, n) -> new MiCitaResponse(uuid(rs, "id"), fechaHora(rs, "programada_para"),
                        rs.getString("medico"), rs.getString("especialidad"),
                        enumeracion(rs, "estado", EstadoCita.class), rs.getString("motivo")))
                .list();
    }

    /** Más recientes primero; sin notas_medicas. */
    public List<MiSolicitudResponse> solicitudes(UUID pacienteId) {
        return jdbc.sql("""
                        SELECT s.id, pr.nombre AS procedimiento, e.nombre AS especialidad,
                               u.nombres || ' ' || u.apellidos AS medico, s.sitio_quirurgico,
                               s.lateralidad::text AS lateralidad, s.estado::text AS estado, s.creado_en,
                               s.enviada_en, c.id AS cirugia_id
                        FROM solicitudes_cirugia s
                                 JOIN procedimientos_quirurgicos pr ON pr.id = s.procedimiento_id
                                 JOIN especialidades e ON e.id = s.especialidad_solicitante_id
                                 JOIN perfiles_profesionales pp ON pp.id = s.medico_solicitante_id
                                 JOIN usuarios u ON u.id = pp.usuario_id
                                 LEFT JOIN cirugias c ON c.solicitud_cirugia_id = s.id
                        WHERE s.paciente_id = :pacienteId
                        ORDER BY s.creado_en DESC, s.id
                        """)
                .param("pacienteId", pacienteId)
                .query((rs, n) -> new MiSolicitudResponse(uuid(rs, "id"), rs.getString("procedimiento"),
                        rs.getString("especialidad"), rs.getString("medico"), rs.getString("sitio_quirurgico"),
                        enumeracion(rs, "lateralidad", Lateralidad.class),
                        enumeracion(rs, "estado", EstadoSolicitudCirugia.class), fechaHora(rs, "creado_en"),
                        fechaHora(rs, "enviada_en"), uuid(rs, "cirugia_id")))
                .list();
    }

    /** Más recientes primero. */
    public List<MiCirugiaResponse> cirugias(UUID pacienteId) {
        return jdbc.sql(SELECT_CIRUGIA + " ORDER BY c.inicio_programado DESC, c.id")
                .param("pacienteId", pacienteId)
                .query((rs, n) -> cirugia(rs))
                .list();
    }

    /** Vacío también si la cirugía existe pero es de otro paciente (no se revela que existe). */
    public Optional<MiCirugiaResponse> cirugia(UUID pacienteId, UUID cirugiaId) {
        return jdbc.sql(SELECT_CIRUGIA + " AND c.id = :cirugiaId")
                .param("pacienteId", pacienteId)
                .param("cirugiaId", cirugiaId)
                .query((rs, n) -> cirugia(rs))
                .optional();
    }

    private static MiCirugiaResponse cirugia(ResultSet rs) throws SQLException {
        return new MiCirugiaResponse(uuid(rs, "id"), uuid(rs, "solicitud_cirugia_id"), rs.getString("procedimiento"),
                rs.getString("sitio_quirurgico"), enumeracion(rs, "lateralidad", Lateralidad.class),
                rs.getString("quirofano"), fechaHora(rs, "inicio_programado"), fechaHora(rs, "fin_programado"),
                enumeracion(rs, "estado", EstadoCirugia.class));
    }
}
