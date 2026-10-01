package com.hackaton.ulibre.alertas;

import static com.hackaton.ulibre.comun.Filas.enumeracion;
import static com.hackaton.ulibre.comun.Filas.fechaHora;
import static com.hackaton.ulibre.comun.Filas.uuid;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class AlertasConsultas {

    private final JdbcClient jdbc;

    public AlertasConsultas(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Vigentes primero; dentro de cada grupo, más severas y más recientes primero. */
    public List<AlertaVista> deCirugia(UUID cirugiaId) {
        return jdbc.sql("""
                        SELECT a.id, rs.codigo AS regla, a.tipo_origen, a.origen_id,
                               a.severidad::text AS severidad, a.bloqueante, a.estado::text AS estado,
                               a.titulo, a.mensaje, a.disparada_en,
                               ur.nombres || ' ' || ur.apellidos AS reconocida_por, a.reconocida_en,
                               us.nombres || ' ' || us.apellidos AS resuelta_por, a.resuelta_en, a.notas_resolucion,
                               ue.nombres || ' ' || ue.apellidos AS excepcion_por,
                               a.excepcion_autorizada_en, a.motivo_excepcion
                        FROM alertas a
                                 LEFT JOIN reglas_seguridad rs ON rs.id = a.regla_seguridad_id
                                 LEFT JOIN usuarios ur ON ur.id = a.reconocida_por_usuario_id
                                 LEFT JOIN usuarios us ON us.id = a.resuelta_por_usuario_id
                                 LEFT JOIN usuarios ue ON ue.id = a.excepcion_autorizada_por_usuario_id
                        WHERE a.cirugia_id = :cirugiaId
                        ORDER BY (a.estado IN ('ABIERTA', 'RECONOCIDA')) DESC, a.severidad DESC, a.disparada_en DESC
                        """)
                .param("cirugiaId", cirugiaId)
                .query((rs, n) -> {
                    EstadoAlerta estado = enumeracion(rs, "estado", EstadoAlerta.class);
                    AlertaVista.Excepcion excepcion = fechaHora(rs, "excepcion_autorizada_en") == null ? null
                            : new AlertaVista.Excepcion(rs.getString("excepcion_por"),
                                    fechaHora(rs, "excepcion_autorizada_en"), rs.getString("motivo_excepcion"));
                    return new AlertaVista(
                            uuid(rs, "id"),
                            rs.getString("regla"),
                            rs.getString("tipo_origen"),
                            uuid(rs, "origen_id"),
                            enumeracion(rs, "severidad", SeveridadAlerta.class),
                            rs.getBoolean("bloqueante"),
                            estado,
                            estado.vigente(),
                            rs.getString("titulo"),
                            rs.getString("mensaje"),
                            fechaHora(rs, "disparada_en"),
                            rs.getString("reconocida_por"),
                            fechaHora(rs, "reconocida_en"),
                            rs.getString("resuelta_por"),
                            fechaHora(rs, "resuelta_en"),
                            rs.getString("notas_resolucion"),
                            excepcion);
                })
                .list();
    }
}
