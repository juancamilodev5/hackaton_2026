package com.hackaton.ulibre.citas;

import static com.hackaton.ulibre.comun.Filas.enumeracion;
import static com.hackaton.ulibre.comun.Filas.fechaHora;
import static com.hackaton.ulibre.comun.Filas.uuid;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.hackaton.ulibre.comun.Pagina;
import com.hackaton.ulibre.comun.Paginacion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Lecturas de citas con nombres de paciente, médico y especialidad (SQL nativo). */
@Component
public class CitasConsultas {

    private static final String SELECT = """
            SELECT c.id, c.programada_para, c.motivo, c.estado::text AS estado, c.creado_en, c.actualizado_en,
                   p.id AS paciente_id, p.nombres || ' ' || p.apellidos AS paciente_nombre,
                   pp.id AS medico_id, u.nombres || ' ' || u.apellidos AS medico_nombre,
                   e.id AS especialidad_id, e.codigo AS especialidad_codigo, e.nombre AS especialidad_nombre
            FROM citas c
                     JOIN pacientes p ON p.id = c.paciente_id
                     JOIN perfiles_profesionales pp ON pp.id = c.medico_id
                     JOIN usuarios u ON u.id = pp.usuario_id
                     JOIN especialidades e ON e.id = c.especialidad_id
            """;

    private final JdbcClient jdbc;

    public CitasConsultas(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Filtro(UUID pacienteId, UUID medicoId, UUID especialidadId, EstadoCita estado,
                         LocalDate desde, LocalDate hasta) {
    }

    /** Ordenadas por fecha programada; {@code hasta} incluye el día completo. */
    public Pagina<CitaResponse> listar(Filtro filtro, Paginacion paginacion) {
        List<String> condiciones = new ArrayList<>();
        Map<String, Object> parametros = new HashMap<>();
        if (filtro.pacienteId() != null) {
            condiciones.add("c.paciente_id = :pacienteId");
            parametros.put("pacienteId", filtro.pacienteId());
        }
        if (filtro.medicoId() != null) {
            condiciones.add("c.medico_id = :medicoId");
            parametros.put("medicoId", filtro.medicoId());
        }
        if (filtro.especialidadId() != null) {
            condiciones.add("c.especialidad_id = :especialidadId");
            parametros.put("especialidadId", filtro.especialidadId());
        }
        if (filtro.estado() != null) {
            condiciones.add("c.estado = CAST(:estado AS estado_cita)");
            parametros.put("estado", filtro.estado().name());
        }
        if (filtro.desde() != null) {
            condiciones.add("c.programada_para >= :desde");
            parametros.put("desde", filtro.desde().atStartOfDay());
        }
        if (filtro.hasta() != null) {
            condiciones.add("c.programada_para < :hasta");
            parametros.put("hasta", filtro.hasta().plusDays(1).atStartOfDay());
        }
        String where = condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);

        long total = jdbc.sql("SELECT count(*) FROM citas c" + where)
                .params(parametros)
                .query(Long.class)
                .single();
        List<CitaResponse> contenido = jdbc.sql(SELECT + where
                        + " ORDER BY c.programada_para, c.id LIMIT :limite OFFSET :desplazamiento")
                .params(parametros)
                .param("limite", paginacion.tamano())
                .param("desplazamiento", paginacion.desplazamiento())
                .query((rs, n) -> fila(rs))
                .list();
        return Pagina.de(contenido, paginacion, total);
    }

    public Optional<CitaResponse> obtener(UUID id) {
        return jdbc.sql(SELECT + " WHERE c.id = :id")
                .param("id", id)
                .query((rs, n) -> fila(rs))
                .optional();
    }

    private static CitaResponse fila(ResultSet rs) throws SQLException {
        return new CitaResponse(
                uuid(rs, "id"),
                new CitaResponse.Referencia(uuid(rs, "paciente_id"), rs.getString("paciente_nombre")),
                new CitaResponse.Referencia(uuid(rs, "medico_id"), rs.getString("medico_nombre")),
                new CitaResponse.Especialidad(uuid(rs, "especialidad_id"), rs.getString("especialidad_codigo"),
                        rs.getString("especialidad_nombre")),
                fechaHora(rs, "programada_para"),
                rs.getString("motivo"),
                enumeracion(rs, "estado", EstadoCita.class),
                fechaHora(rs, "creado_en"),
                fechaHora(rs, "actualizado_en"));
    }
}
