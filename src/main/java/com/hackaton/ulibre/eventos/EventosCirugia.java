package com.hackaton.ulibre.eventos;

import static com.hackaton.ulibre.comun.Filas.fechaHora;
import static com.hackaton.ulibre.comun.Filas.uuid;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.CirugiasConsultas;
import com.hackaton.ulibre.cirugias.ParticipanteVista;
import com.hackaton.ulibre.comun.UsuarioActual;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Timeline operacional de la cirugía (eventos_cirugia, solo-agregar por trigger). No es la
 * auditoría administrativa (registros_auditoria): aquí va lo que ocurrió en el flujo quirúrgico.
 * Se escribe en la misma transacción que la acción que lo origina.
 */
@Component
public class EventosCirugia {

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;
    private final CirugiasConsultas cirugias;

    public EventosCirugia(JdbcClient jdbc, JsonMapper json, Clock clock, CirugiasConsultas cirugias) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
        this.cirugias = cirugias;
    }

    /**
     * @param actorAsignacionId asignación con la que actuó (quién y en qué rol), si participa en la cirugía
     * @param datos             detalle pequeño (ids, estados, cantidades); se guarda como jsonb. Sin datos clínicos libres.
     */
    public void registrar(UUID cirugiaId, TipoEvento tipo, UUID actorAsignacionId, String tipoEntidad,
            UUID entidadId, Map<String, ?> datos) {
        jdbc.sql("""
                        INSERT INTO eventos_cirugia
                            (cirugia_id, tipo_evento, actor_usuario_id, actor_asignacion_id,
                             tipo_entidad_relacionada, entidad_relacionada_id, datos, ocurrido_en)
                        VALUES (:cirugiaId, :tipo, :usuarioId, :asignacionId, :tipoEntidad, :entidadId,
                                CAST(:datos AS jsonb), :ocurridoEn)
                        """)
                .param("cirugiaId", cirugiaId)
                .param("tipo", tipo.name())
                .param("usuarioId", UsuarioActual.id())
                .param("asignacionId", actorAsignacionId)
                .param("tipoEntidad", tipoEntidad)
                .param("entidadId", entidadId)
                .param("datos", datos == null || datos.isEmpty() ? null : json.writeValueAsString(datos))
                .param("ocurridoEn", LocalDateTime.now(clock))
                .update();
    }

    /** Timeline completo en orden cronológico; 2 consultas fijas (participantes + eventos). */
    public List<EventoVista> deCirugia(UUID cirugiaId) {
        Map<UUID, ParticipanteVista> participantes = cirugias.participantes(cirugiaId);
        return jdbc.sql("""
                        SELECT e.id, e.tipo_evento, e.actor_usuario_id,
                               u.nombres || ' ' || u.apellidos AS actor, e.actor_asignacion_id,
                               e.tipo_entidad_relacionada, e.entidad_relacionada_id, e.datos::text AS datos,
                               e.ocurrido_en
                        FROM eventos_cirugia e
                                 LEFT JOIN usuarios u ON u.id = e.actor_usuario_id
                        WHERE e.cirugia_id = :cirugiaId
                        ORDER BY e.ocurrido_en, e.id
                        """)
                .param("cirugiaId", cirugiaId)
                .query((rs, n) -> {
                    UUID asignacion = uuid(rs, "actor_asignacion_id");
                    return new EventoVista(
                            uuid(rs, "id"),
                            rs.getString("tipo_evento"),
                            fechaHora(rs, "ocurrido_en"),
                            uuid(rs, "actor_usuario_id"),
                            rs.getString("actor"),
                            asignacion == null ? null : participantes.get(asignacion),
                            rs.getString("tipo_entidad_relacionada"),
                            uuid(rs, "entidad_relacionada_id"),
                            rs.getString("datos"));
                })
                .list();
    }
}
