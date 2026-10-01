package com.hackaton.ulibre.tablero;

import static com.hackaton.ulibre.comun.Filas.entero;
import static com.hackaton.ulibre.comun.Filas.enumeracion;
import static com.hackaton.ulibre.comun.Filas.fechaHora;
import static com.hackaton.ulibre.comun.Filas.uuid;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.ParticipanteVista;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Recuentos e hitos de una cirugía. */
@Component
public class TableroConsultas {

    private final JdbcClient jdbc;

    public TableroConsultas(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<RecuentoVista> recuentos(UUID cirugiaId, Map<UUID, ParticipanteVista> participantes) {
        Map<UUID, Map<EtapaRecuento, List<RecuentoVista.ConfirmacionConteo>>> confirmaciones = new HashMap<>();
        jdbc.sql("""
                        SELECT id, recuento_cirugia_id, etapa::text AS etapa, asignacion_personal_id, confirmado_en, notas
                        FROM confirmaciones_recuento
                        WHERE cirugia_id = :cirugiaId
                        ORDER BY confirmado_en
                        """)
                .param("cirugiaId", cirugiaId)
                .query(rs -> {
                    confirmaciones.computeIfAbsent(uuid(rs, "recuento_cirugia_id"), k -> etapasVacias())
                            .get(enumeracion(rs, "etapa", EtapaRecuento.class))
                            .add(new RecuentoVista.ConfirmacionConteo(
                                    uuid(rs, "id"),
                                    participantes.get(uuid(rs, "asignacion_personal_id")),
                                    fechaHora(rs, "confirmado_en"),
                                    rs.getString("notas")));
                });

        return jdbc.sql("""
                        SELECT id, tipo_recuento::text AS tipo_recuento, descripcion, instrumento_cirugia_id,
                               cantidad_inicial, cantidad_agregada, cantidad_final, cantidad_esperada, estado_recuento,
                               registrado_inicial_por_asignacion_id, registrado_inicial_en,
                               registrado_final_por_asignacion_id, registrado_final_en, notas
                        FROM v_recuentos_cirugia
                        WHERE cirugia_id = :cirugiaId
                        ORDER BY tipo_recuento, descripcion NULLS FIRST
                        """)
                .param("cirugiaId", cirugiaId)
                .query((rs, n) -> {
                    UUID id = uuid(rs, "id");
                    UUID finalPor = uuid(rs, "registrado_final_por_asignacion_id");
                    return new RecuentoVista(
                            id,
                            enumeracion(rs, "tipo_recuento", TipoRecuento.class),
                            rs.getString("descripcion"),
                            uuid(rs, "instrumento_cirugia_id"),
                            rs.getInt("cantidad_inicial"),
                            rs.getInt("cantidad_agregada"),
                            entero(rs, "cantidad_final"),
                            rs.getInt("cantidad_esperada"),
                            enumeracion(rs, "estado_recuento", EstadoRecuento.class),
                            participantes.get(uuid(rs, "registrado_inicial_por_asignacion_id")),
                            fechaHora(rs, "registrado_inicial_en"),
                            finalPor == null ? null : participantes.get(finalPor),
                            fechaHora(rs, "registrado_final_en"),
                            rs.getString("notas"),
                            confirmaciones.getOrDefault(id, etapasVacias()));
                })
                .list();
    }

    /** Solo hitos vigentes (no anulados), en orden cronológico. */
    public List<HitoVista> hitos(UUID cirugiaId, Map<UUID, ParticipanteVista> participantes) {
        return jdbc.sql("""
                        SELECT id, tipo_hito::text AS tipo_hito, ocurrido_en, registrado_por_asignacion_id, notas,
                               corrige_hito_id
                        FROM hitos_cirugia
                        WHERE cirugia_id = :cirugiaId
                          AND anulado_en IS NULL
                        ORDER BY ocurrido_en
                        """)
                .param("cirugiaId", cirugiaId)
                .query((rs, n) -> {
                    UUID registradoPor = uuid(rs, "registrado_por_asignacion_id");
                    return new HitoVista(
                            uuid(rs, "id"),
                            enumeracion(rs, "tipo_hito", TipoHito.class),
                            fechaHora(rs, "ocurrido_en"),
                            registradoPor == null ? null : participantes.get(registradoPor),
                            rs.getString("notas"),
                            uuid(rs, "corrige_hito_id"));
                })
                .list();
    }

    private static Map<EtapaRecuento, List<RecuentoVista.ConfirmacionConteo>> etapasVacias() {
        Map<EtapaRecuento, List<RecuentoVista.ConfirmacionConteo>> etapas = new EnumMap<>(EtapaRecuento.class);
        for (EtapaRecuento etapa : EtapaRecuento.values()) {
            etapas.put(etapa, new ArrayList<>());
        }
        return etapas;
    }
}
