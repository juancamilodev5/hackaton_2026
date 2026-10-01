package com.hackaton.ulibre.instrumental;

import static com.hackaton.ulibre.comun.Filas.fechaHora;
import static com.hackaton.ulibre.comun.Filas.uuid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.ParticipanteVista;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Instrumental (snapshot) de una cirugía: 2 consultas fijas (sets e instrumentos). */
@Component
public class InstrumentalConsultas {

    private final JdbcClient jdbc;

    public InstrumentalConsultas(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public InstrumentalVista deCirugia(UUID cirugiaId, Map<UUID, ParticipanteVista> participantes) {
        Map<UUID, List<InstrumentalVista.Instrumento>> porSet = new HashMap<>();
        List<InstrumentalVista.Instrumento> directos = new ArrayList<>();
        List<InstrumentalVista.Faltante> faltantes = new ArrayList<>();

        jdbc.sql("""
                        SELECT id, set_cirugia_id, codigo_instrumento, nombre_instrumento, es_requerido,
                               cantidad_requerida, cantidad_preparada, preparado_por_asignacion_id, preparado_en, notas
                        FROM instrumentos_cirugia
                        WHERE cirugia_id = :cirugiaId
                        ORDER BY nombre_instrumento, id
                        """)
                .param("cirugiaId", cirugiaId)
                .query(rs -> {
                    UUID preparadoPor = uuid(rs, "preparado_por_asignacion_id");
                    InstrumentalVista.Instrumento instrumento = new InstrumentalVista.Instrumento(
                            uuid(rs, "id"),
                            rs.getString("codigo_instrumento"),
                            rs.getString("nombre_instrumento"),
                            rs.getBoolean("es_requerido"),
                            rs.getInt("cantidad_requerida"),
                            rs.getInt("cantidad_preparada"),
                            preparadoPor == null ? null : participantes.get(preparadoPor),
                            fechaHora(rs, "preparado_en"),
                            rs.getString("notas"));
                    UUID setId = uuid(rs, "set_cirugia_id");
                    if (setId != null) {
                        porSet.computeIfAbsent(setId, k -> new ArrayList<>()).add(instrumento);
                    } else {
                        directos.add(instrumento);
                        if (instrumento.esRequerido() && instrumento.cantidadPreparada() < instrumento.cantidadRequerida()) {
                            faltantes.add(new InstrumentalVista.Faltante("INSTRUMENTO", instrumento.codigo(),
                                    instrumento.nombre(), instrumento.cantidadRequerida(), instrumento.cantidadPreparada()));
                        }
                    }
                });

        List<InstrumentalVista.SetInstrumental> sets = jdbc.sql("""
                        SELECT id, codigo_set, nombre_set, es_requerido, cantidad_requerida, cantidad_preparada,
                               preparado_por_asignacion_id, preparado_en, notas
                        FROM sets_instrumentales_cirugia
                        WHERE cirugia_id = :cirugiaId
                        ORDER BY codigo_set
                        """)
                .param("cirugiaId", cirugiaId)
                .query((rs, n) -> {
                    UUID id = uuid(rs, "id");
                    UUID preparadoPor = uuid(rs, "preparado_por_asignacion_id");
                    int requerida = rs.getInt("cantidad_requerida");
                    int preparada = rs.getInt("cantidad_preparada");
                    return new InstrumentalVista.SetInstrumental(
                            id,
                            rs.getString("codigo_set"),
                            rs.getString("nombre_set"),
                            rs.getBoolean("es_requerido"),
                            requerida,
                            preparada,
                            preparada >= requerida,
                            preparadoPor == null ? null : participantes.get(preparadoPor),
                            fechaHora(rs, "preparado_en"),
                            rs.getString("notas"),
                            porSet.getOrDefault(id, List.of()));
                })
                .list();

        // Sets primero, como en el mensaje de la base al bloquear el inicio de la cirugía
        List<InstrumentalVista.Faltante> faltantesOrdenados = new ArrayList<>();
        sets.stream()
                .filter(s -> s.esRequerido() && !s.preparado())
                .map(s -> new InstrumentalVista.Faltante("SET", s.codigo(), s.nombre(), s.cantidadRequerida(),
                        s.cantidadPreparada()))
                .forEach(faltantesOrdenados::add);
        faltantesOrdenados.addAll(faltantes);

        return new InstrumentalVista(faltantesOrdenados.isEmpty(), faltantesOrdenados, sets, directos);
    }
}
