package com.hackaton.ulibre.alertas;

import static com.hackaton.ulibre.comun.Filas.uuid;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.hackaton.ulibre.checklist.EstadoFaseChecklist;
import com.hackaton.ulibre.checklist.EstadoItemChecklist;
import com.hackaton.ulibre.cirugias.EstadoCirugia;
import com.hackaton.ulibre.comun.Filas;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Foto de la cirugía que necesitan las reglas, cargada con un número fijo de consultas (sin N+1).
 * Los criterios que también usa la base (instrumental faltante, estado del recuento) se leen con
 * la misma consulta o vista que ella, para no tener dos fórmulas distintas.
 */
record ContextoReglas(
        UUID cirugiaId,
        EstadoCirugia estado,
        boolean cirugiaIniciada,
        Preoperatorio preoperatorio,
        boolean sitioQuirurgicoRegistrado,
        int alergiasPaciente,
        boolean hayOperador,
        List<String> requerimientosSinCubrir,
        boolean instrumentalPreparado,
        boolean procedimientoTieneInstrumental,
        List<String> instrumentalFaltante,
        List<String> recuentosConDiscrepancia,
        List<Fase> fases) {

    record Preoperatorio(BigDecimal pesoKg, BigDecimal tallaCm, LocalDateTime validadoEn) {
    }

    record Fase(UUID id, String codigo, String nombre, int orden, EstadoFaseChecklist estado, List<Item> items) {
    }

    /** confirmado: existe al menos una confirmación CONFIRMADO de quien cubre el rol del ítem. */
    record Item(UUID id, String codigo, String etiqueta, boolean obligatorio, boolean bloqueante,
                boolean exigeRol, EstadoItemChecklist estado, boolean confirmado) {

        /** Respondido y, si tiene rol responsable, confirmado (lo exigible a un ítem obligatorio). */
        boolean completo() {
            return estado == EstadoItemChecklist.NO_APLICA
                    || (estado != EstadoItemChecklist.PENDIENTE && (!exigeRol || confirmado));
        }

        /** Hecho y, si el ítem tiene rol responsable, confirmado por esa persona (NO_APLICA no se confirma). */
        boolean satisfecho() {
            return estado == EstadoItemChecklist.NO_APLICA
                    || (estado == EstadoItemChecklist.COMPLETADO && (!exigeRol || confirmado));
        }
    }

    Optional<Item> item(String codigo) {
        return fases.stream().flatMap(f -> f.items().stream()).filter(i -> i.codigo().equals(codigo)).findFirst();
    }

    Optional<Fase> fase(String codigo) {
        return fases.stream().filter(f -> f.codigo().equals(codigo)).findFirst();
    }

    static ContextoReglas cargar(JdbcClient jdbc, UUID cirugiaId) {
        record Cabecera(EstadoCirugia estado, boolean cirugiaIniciada, boolean tienePreop, BigDecimal peso,
                        BigDecimal talla, LocalDateTime validadoEn, boolean sitio, int alergias, boolean operador,
                        boolean preparado, boolean procedimientoTieneInstrumental) {
        }
        Cabecera c = jdbc.sql("""
                        SELECT c.estado::text AS estado,
                               EXISTS (SELECT 1 FROM hitos_cirugia h WHERE h.cirugia_id = c.id
                                         AND h.tipo_hito = 'CIRUGIA_INICIADA' AND h.anulado_en IS NULL) AS iniciada,
                               d.id IS NOT NULL AS tiene_preop, d.peso_kg, d.talla_cm, d.validado_en,
                               s.sitio_quirurgico IS NOT NULL AND btrim(s.sitio_quirurgico) <> '' AS sitio,
                               CASE WHEN d.id IS NOT NULL
                                    THEN CASE WHEN jsonb_typeof(d.copia_alergias) = 'array'
                                              THEN jsonb_array_length(d.copia_alergias) ELSE 0 END
                                    ELSE (SELECT count(*) FROM alergias_paciente ap
                                          WHERE ap.paciente_id = s.paciente_id AND ap.activo) END AS alergias,
                               EXISTS (SELECT 1 FROM asignaciones_personal_cirugia a WHERE a.cirugia_id = c.id
                                         AND a.es_operador_tablero AND a.estado IN ('ASIGNADA', 'CONFIRMADA')) AS operador,
                               (EXISTS (SELECT 1 FROM sets_instrumentales_cirugia x WHERE x.cirugia_id = c.id)
                                OR EXISTS (SELECT 1 FROM instrumentos_cirugia x WHERE x.cirugia_id = c.id)) AS preparado,
                               (EXISTS (SELECT 1 FROM sets_predeterminados_procedimiento x WHERE x.procedimiento_id = s.procedimiento_id)
                                OR EXISTS (SELECT 1 FROM instrumentos_predeterminados_procedimiento x
                                           WHERE x.procedimiento_id = s.procedimiento_id)) AS proc_instrumental
                        FROM cirugias c
                                 JOIN solicitudes_cirugia s ON s.id = c.solicitud_cirugia_id
                                 LEFT JOIN datos_preoperatorios_cirugia d ON d.cirugia_id = c.id
                        WHERE c.id = :cirugiaId
                        """)
                .param("cirugiaId", cirugiaId)
                .query((rs, n) -> new Cabecera(
                        Filas.enumeracion(rs, "estado", EstadoCirugia.class),
                        rs.getBoolean("iniciada"),
                        rs.getBoolean("tiene_preop"),
                        rs.getBigDecimal("peso_kg"),
                        rs.getBigDecimal("talla_cm"),
                        Filas.fechaHora(rs, "validado_en"),
                        rs.getBoolean("sitio"),
                        rs.getInt("alergias"),
                        rs.getBoolean("operador"),
                        rs.getBoolean("preparado"),
                        rs.getBoolean("proc_instrumental")))
                .single();

        List<String> sinCubrir = jdbc.sql("""
                        SELECT rc.nombre || ' (' || (r.cantidad - count(a.id)) || ' de ' || r.cantidad || ')' AS faltante
                        FROM cirugias c
                                 JOIN requerimientos_roles_solicitud r ON r.solicitud_cirugia_id = c.solicitud_cirugia_id
                                 JOIN roles_clinicos rc ON rc.id = r.rol_clinico_id
                                 LEFT JOIN asignaciones_personal_cirugia a
                                           ON a.requerimiento_rol_id = r.id AND a.cirugia_id = c.id
                                               AND a.estado IN ('ASIGNADA', 'CONFIRMADA')
                        WHERE c.id = :cirugiaId AND r.es_requerido
                        GROUP BY r.id, rc.nombre, r.cantidad
                        HAVING count(a.id) < r.cantidad
                        ORDER BY rc.nombre
                        """)
                .param("cirugiaId", cirugiaId)
                .query(String.class)
                .list();

        // Mismo criterio que fn_exigir_instrumental_completo: sets requeridos + instrumentos directos requeridos
        List<String> faltante = jdbc.sql("""
                        SELECT nombre FROM (
                            SELECT nombre_set AS nombre, 0 AS grupo FROM sets_instrumentales_cirugia
                            WHERE cirugia_id = :cirugiaId AND es_requerido AND cantidad_preparada < cantidad_requerida
                            UNION ALL
                            SELECT nombre_instrumento, 1 FROM instrumentos_cirugia
                            WHERE cirugia_id = :cirugiaId AND set_cirugia_id IS NULL
                              AND es_requerido AND cantidad_preparada < cantidad_requerida
                        ) f ORDER BY grupo, nombre
                        """)
                .param("cirugiaId", cirugiaId)
                .query(String.class)
                .list();

        List<String> discrepancias = jdbc.sql("""
                        SELECT tipo_recuento::text || COALESCE(' ' || descripcion, '')
                               || ' (esperado ' || cantidad_esperada || ', final ' || cantidad_final || ')'
                        FROM v_recuentos_cirugia
                        WHERE cirugia_id = :cirugiaId AND estado_recuento = 'DISCREPANCIA'
                        ORDER BY tipo_recuento, descripcion NULLS FIRST
                        """)
                .param("cirugiaId", cirugiaId)
                .query(String.class)
                .list();

        record FilaItem(UUID faseId, String faseCodigo, String faseNombre, int faseOrden,
                        EstadoFaseChecklist faseEstado, Item item) {
        }
        List<FilaItem> filas = jdbc.sql("""
                        SELECT f.id AS fase_id, f.codigo_fase, f.nombre_fase, f.orden AS fase_orden,
                               f.estado::text AS fase_estado,
                               i.id AS item_id, i.codigo_item, i.etiqueta, i.obligatorio, i.bloqueante,
                               i.rol_clinico_responsable_id IS NOT NULL AS exige_rol, i.estado::text AS item_estado,
                               EXISTS (SELECT 1 FROM confirmaciones_item_checklist ci
                                       WHERE ci.item_checklist_cirugia_id = i.id
                                         AND ci.resultado = 'CONFIRMADO') AS confirmado
                        FROM fases_checklist_cirugia f
                                 LEFT JOIN items_checklist_cirugia i ON i.fase_cirugia_id = f.id
                        WHERE f.cirugia_id = :cirugiaId
                        ORDER BY f.orden, i.orden
                        """)
                .param("cirugiaId", cirugiaId)
                .query((rs, n) -> new FilaItem(
                        uuid(rs, "fase_id"),
                        rs.getString("codigo_fase"),
                        rs.getString("nombre_fase"),
                        rs.getInt("fase_orden"),
                        Filas.enumeracion(rs, "fase_estado", EstadoFaseChecklist.class),
                        uuid(rs, "item_id") == null ? null : new Item(
                                uuid(rs, "item_id"),
                                rs.getString("codigo_item"),
                                rs.getString("etiqueta"),
                                rs.getBoolean("obligatorio"),
                                rs.getBoolean("bloqueante"),
                                rs.getBoolean("exige_rol"),
                                Filas.enumeracion(rs, "item_estado", EstadoItemChecklist.class),
                                rs.getBoolean("confirmado"))))
                .list();
        Map<UUID, List<FilaItem>> porFase = filas.stream()
                .collect(Collectors.groupingBy(FilaItem::faseId, java.util.LinkedHashMap::new, Collectors.toList()));
        List<Fase> fases = new ArrayList<>();
        porFase.forEach((id, grupo) -> {
            FilaItem primera = grupo.getFirst();
            fases.add(new Fase(id, primera.faseCodigo(), primera.faseNombre(), primera.faseOrden(),
                    primera.faseEstado(), grupo.stream().map(FilaItem::item).filter(i -> i != null).toList()));
        });

        return new ContextoReglas(
                cirugiaId,
                c.estado(),
                c.cirugiaIniciada(),
                c.tienePreop() ? new Preoperatorio(c.peso(), c.talla(), c.validadoEn()) : null,
                c.sitio(),
                c.alergias(),
                c.operador(),
                sinCubrir,
                c.preparado(),
                c.procedimientoTieneInstrumental(),
                faltante,
                discrepancias,
                fases);
    }
}
