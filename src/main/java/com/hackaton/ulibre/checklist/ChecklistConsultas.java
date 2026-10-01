package com.hackaton.ulibre.checklist;

import static com.hackaton.ulibre.comun.Filas.enumeracion;
import static com.hackaton.ulibre.comun.Filas.fechaHora;
import static com.hackaton.ulibre.comun.Filas.uuid;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.hackaton.ulibre.catalogos.RolClinicoVista;
import com.hackaton.ulibre.cirugias.ParticipanteVista;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Checklist ejecutado de una cirugía: 4 consultas fijas (cabecera, fases, ítems, confirmaciones). */
@Component
public class ChecklistConsultas {

    private final JdbcClient jdbc;

    public ChecklistConsultas(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** null si la cirugía aún no tiene checklist (no se ha ejecutado fn_iniciar_checklist). */
    public ChecklistVista deCirugia(UUID cirugiaId, Map<UUID, ParticipanteVista> participantes) {
        record Cabecera(UUID id, ChecklistVista.Plantilla plantilla, EstadoChecklist estado,
                        LocalDateTime iniciadoEn, LocalDateTime completadoEn) {
        }
        Optional<Cabecera> cabecera = jdbc.sql("""
                        SELECT id, codigo_plantilla, nombre_plantilla, version_plantilla, estado::text AS estado,
                               iniciado_en, completado_en
                        FROM checklists_cirugia
                        WHERE cirugia_id = :cirugiaId
                        ORDER BY creado_en DESC
                        LIMIT 1
                        """)
                .param("cirugiaId", cirugiaId)
                .query((rs, n) -> new Cabecera(
                        uuid(rs, "id"),
                        new ChecklistVista.Plantilla(rs.getString("codigo_plantilla"), rs.getString("nombre_plantilla"),
                                rs.getInt("version_plantilla")),
                        enumeracion(rs, "estado", EstadoChecklist.class),
                        fechaHora(rs, "iniciado_en"),
                        fechaHora(rs, "completado_en")))
                .optional();
        if (cabecera.isEmpty()) {
            return null;
        }
        UUID checklistId = cabecera.get().id();

        Map<UUID, List<ChecklistVista.Confirmacion>> confirmacionesPorItem = new HashMap<>();
        jdbc.sql("""
                        SELECT c.id, c.item_checklist_cirugia_id, c.asignacion_personal_id,
                               c.resultado::text AS resultado, c.confirmado_en, c.notas
                        FROM confirmaciones_item_checklist c
                                 JOIN items_checklist_cirugia i ON i.id = c.item_checklist_cirugia_id
                                 JOIN fases_checklist_cirugia f ON f.id = i.fase_cirugia_id
                        WHERE f.checklist_cirugia_id = :checklistId
                        ORDER BY c.confirmado_en
                        """)
                .param("checklistId", checklistId)
                .query(rs -> {
                    confirmacionesPorItem
                            .computeIfAbsent(uuid(rs, "item_checklist_cirugia_id"), k -> new ArrayList<>())
                            .add(new ChecklistVista.Confirmacion(
                                    uuid(rs, "id"),
                                    participantes.get(uuid(rs, "asignacion_personal_id")),
                                    enumeracion(rs, "resultado", ResultadoConfirmacion.class),
                                    fechaHora(rs, "confirmado_en"),
                                    rs.getString("notas")));
                });

        Map<UUID, List<ChecklistVista.Item>> itemsPorFase = new HashMap<>();
        jdbc.sql("""
                        SELECT i.id, i.fase_cirugia_id, i.codigo_item, i.etiqueta, i.descripcion, i.tipo_respuesta,
                               i.obligatorio, i.bloqueante, rc.codigo AS rol_codigo, rc.nombre AS rol_nombre,
                               i.estado::text AS estado, i.respuesta::text AS respuesta,
                               i.registrado_por_asignacion_id, i.registrado_en, i.notas
                        FROM items_checklist_cirugia i
                                 JOIN fases_checklist_cirugia f ON f.id = i.fase_cirugia_id
                                 LEFT JOIN roles_clinicos rc ON rc.id = i.rol_clinico_responsable_id
                        WHERE f.checklist_cirugia_id = :checklistId
                        ORDER BY f.orden, i.orden
                        """)
                .param("checklistId", checklistId)
                .query(rs -> {
                    UUID itemId = uuid(rs, "id");
                    UUID registradoPor = uuid(rs, "registrado_por_asignacion_id");
                    itemsPorFase.computeIfAbsent(uuid(rs, "fase_cirugia_id"), k -> new ArrayList<>())
                            .add(new ChecklistVista.Item(
                                    itemId,
                                    rs.getString("codigo_item"),
                                    rs.getString("etiqueta"),
                                    rs.getString("descripcion"),
                                    rs.getString("tipo_respuesta"),
                                    rs.getBoolean("obligatorio"),
                                    rs.getBoolean("bloqueante"),
                                    RolClinicoVista.de(rs, "rol_codigo", "rol_nombre"),
                                    enumeracion(rs, "estado", EstadoItemChecklist.class),
                                    rs.getString("respuesta"),
                                    registradoPor == null ? null : participantes.get(registradoPor),
                                    fechaHora(rs, "registrado_en"),
                                    rs.getString("notas"),
                                    confirmacionesPorItem.getOrDefault(itemId, List.of())));
                });

        List<ChecklistVista.Fase> fases = jdbc.sql("""
                        SELECT id, codigo_fase, nombre_fase, orden, estado::text AS estado, iniciada_en,
                               cerrada_por_asignacion_id, cerrada_en, notas
                        FROM fases_checklist_cirugia
                        WHERE checklist_cirugia_id = :checklistId
                        ORDER BY orden
                        """)
                .param("checklistId", checklistId)
                .query((rs, n) -> {
                    UUID faseId = uuid(rs, "id");
                    UUID cerradaPor = uuid(rs, "cerrada_por_asignacion_id");
                    return new ChecklistVista.Fase(
                            faseId,
                            rs.getString("codigo_fase"),
                            rs.getString("nombre_fase"),
                            rs.getInt("orden"),
                            enumeracion(rs, "estado", EstadoFaseChecklist.class),
                            fechaHora(rs, "iniciada_en"),
                            cerradaPor == null ? null : participantes.get(cerradaPor),
                            fechaHora(rs, "cerrada_en"),
                            rs.getString("notas"),
                            itemsPorFase.getOrDefault(faseId, List.of()));
                })
                .list();

        Cabecera c = cabecera.get();
        return new ChecklistVista(c.id(), c.plantilla(), c.estado(), c.iniciadoEn(), c.completadoEn(), fases);
    }
}
