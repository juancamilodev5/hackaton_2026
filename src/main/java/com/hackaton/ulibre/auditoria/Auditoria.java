package com.hackaton.ulibre.auditoria;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import com.hackaton.ulibre.comun.UsuarioActual;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Auditoría administrativa (registros_auditoria, solo-agregar por trigger): quién cambió qué
 * entidad, cuándo, y los valores antes/después. Se escribe en la misma transacción que el cambio.
 *
 * <p>Los valores se pasan como los DTOs de respuesta (nunca la entidad): así no se cuelan
 * columnas sensibles como hash_contrasena. Lo que ocurre dentro de una cirugía va a
 * eventos_cirugia, no aquí.
 */
@Component
public class Auditoria {

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public Auditoria(JdbcClient jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /**
     * @param tipoEntidad nombre de la tabla afectada (p. ej. "especialidades")
     * @param anteriores  estado previo (null en altas)
     * @param nuevos      estado resultante (null en borrados)
     */
    public void registrar(String tipoEntidad, UUID entidadId, AccionAuditoria accion, Object anteriores,
            Object nuevos) {
        jdbc.sql("""
                        INSERT INTO registros_auditoria
                            (usuario_id, tipo_entidad, entidad_id, accion, valores_anteriores, valores_nuevos, ocurrido_en)
                        VALUES (:usuarioId, :tipoEntidad, :entidadId, :accion,
                                CAST(:anteriores AS jsonb), CAST(:nuevos AS jsonb), :ocurridoEn)
                        """)
                .param("usuarioId", UsuarioActual.id())
                .param("tipoEntidad", tipoEntidad)
                .param("entidadId", entidadId)
                .param("accion", accion.name())
                .param("anteriores", anteriores == null ? null : json.writeValueAsString(anteriores))
                .param("nuevos", nuevos == null ? null : json.writeValueAsString(nuevos))
                .param("ocurridoEn", LocalDateTime.now(clock))
                .update();
    }
}
