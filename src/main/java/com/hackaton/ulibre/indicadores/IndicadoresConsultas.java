package com.hackaton.ulibre.indicadores;

import static com.hackaton.ulibre.comun.Filas.enumeracion;
import static com.hackaton.ulibre.comun.Filas.fechaHora;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.EstadoCirugia;
import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.eventos.EventosCirugia;
import com.hackaton.ulibre.tablero.TipoHito;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Indicadores calculados sobre los datos reales (cirugías, hitos vigentes, checklist, alertas,
 * recuentos e incidentes). Una consulta agregada por sección, sin N+1. El esquema no trae vistas de
 * indicadores (solo v_recuentos_cirugia, que se usa para los recuentos).
 */
@Component
public class IndicadoresConsultas {

    /** Cirugías del rango; todas las secciones parten de aquí. */
    private static final String BASE = """
            WITH base AS (
                SELECT id, estado, inicio_programado FROM cirugias
                WHERE inicio_programado >= :desde AND inicio_programado < :hasta
            )
            """;

    private final JdbcClient jdbc;
    private final EventosCirugia eventos;

    public IndicadoresConsultas(JdbcClient jdbc, EventosCirugia eventos) {
        this.jdbc = jdbc;
        this.eventos = eventos;
    }

    public IndicadoresResponse indicadores(LocalDate desde, LocalDate hasta) {
        Map<String, Object> rango = Map.of("desde", desde.atStartOfDay(), "hasta", hasta.plusDays(1).atStartOfDay());
        return new IndicadoresResponse(desde, hasta, cirugias(rango), tiempos(rango), checklist(rango),
                alertas(rango), recuentos(rango), incidentes(rango));
    }

    private IndicadoresResponse.Cirugias cirugias(Map<String, Object> rango) {
        Map<String, Long> porEstado = conteo(BASE + "SELECT estado::text AS clave, count(*) AS n FROM base GROUP BY estado",
                rango);
        long total = porEstado.values().stream().mapToLong(Long::longValue).sum();
        return new IndicadoresResponse.Cirugias(total, porEstado,
                porEstado.getOrDefault(EstadoCirugia.COMPLETADA.name(), 0L),
                porEstado.getOrDefault(EstadoCirugia.CANCELADA.name(), 0L),
                porEstado.getOrDefault(EstadoCirugia.SUSPENDIDA.name(), 0L));
    }

    private Map<Tramo, IndicadoresResponse.Medida> tiempos(Map<String, Object> rango) {
        StringBuilder sql = new StringBuilder(BASE).append("""
                , pivote AS (
                    SELECT b.id, b.inicio_programado""").append(Tramo.columnasPivoteSql()).append("""

                    FROM base b
                             LEFT JOIN hitos_cirugia h ON h.cirugia_id = b.id AND h.anulado_en IS NULL
                    GROUP BY b.id, b.inicio_programado
                ), medidas AS (
                    SELECT 1 AS uno""");
        for (Tramo tramo : Tramo.values()) {
            sql.append(", ").append(tramo.expresionSql()).append(" AS ").append(tramo.name().toLowerCase());
        }
        sql.append(" FROM pivote) SELECT 1 AS uno");
        for (Tramo tramo : Tramo.values()) {
            String c = tramo.name().toLowerCase();
            sql.append(", avg(").append(c).append(") AS ").append(c).append("_prom")
                    .append(", min(").append(c).append(") AS ").append(c).append("_min")
                    .append(", max(").append(c).append(") AS ").append(c).append("_max")
                    .append(", count(").append(c).append(") AS ").append(c).append("_n");
        }
        sql.append(" FROM medidas");
        return jdbc.sql(sql.toString())
                .params(rango)
                .query((rs, n) -> {
                    Map<Tramo, IndicadoresResponse.Medida> medidas = new EnumMap<>(Tramo.class);
                    for (Tramo tramo : Tramo.values()) {
                        String c = tramo.name().toLowerCase();
                        medidas.put(tramo, new IndicadoresResponse.Medida(decimal(rs.getObject(c + "_prom")),
                                decimal(rs.getObject(c + "_min")), decimal(rs.getObject(c + "_max")),
                                rs.getLong(c + "_n")));
                    }
                    return medidas;
                })
                .single();
    }

    private IndicadoresResponse.Checklist checklist(Map<String, Object> rango) {
        record Fila(long conChecklist, long completados, long cirugiasCompletadas, long completadasConChecklist) {
        }
        Fila fila = jdbc.sql(BASE + """
                        SELECT count(ch.id) AS con_checklist,
                               count(*) FILTER (WHERE ch.estado = 'COMPLETADO') AS completados,
                               count(*) FILTER (WHERE b.estado = 'COMPLETADA') AS cirugias_completadas,
                               count(*) FILTER (WHERE b.estado = 'COMPLETADA' AND ch.estado = 'COMPLETADO')
                                   AS completadas_con_checklist
                        FROM base b
                                 LEFT JOIN checklists_cirugia ch ON ch.cirugia_id = b.id
                        """)
                .params(rango)
                .query((rs, n) -> new Fila(rs.getLong("con_checklist"), rs.getLong("completados"),
                        rs.getLong("cirugias_completadas"), rs.getLong("completadas_con_checklist")))
                .single();
        Map<String, Long> items = conteo(BASE + """
                SELECT i.estado::text AS clave, count(*) AS n
                FROM items_checklist_cirugia i JOIN base b ON b.id = i.cirugia_id
                GROUP BY i.estado
                """, rango);
        Double adherencia = fila.cirugiasCompletadas() == 0 ? null
                : redondear(100.0 * fila.completadasConChecklist() / fila.cirugiasCompletadas());
        return new IndicadoresResponse.Checklist(fila.conChecklist(), fila.completados(), fila.cirugiasCompletadas(),
                adherencia, items);
    }

    private IndicadoresResponse.Alertas alertas(Map<String, Object> rango) {
        record Grupo(String severidad, String regla, boolean bloqueante, boolean vigente, boolean excepcion,
                     long n, long cerradas, double minutosCierre) {
        }
        List<Grupo> grupos = jdbc.sql(BASE + """
                        SELECT a.severidad::text AS severidad, coalesce(r.codigo, 'MANUAL') AS regla, a.bloqueante,
                               a.estado IN ('ABIERTA', 'RECONOCIDA') AS vigente,
                               a.excepcion_autorizada_en IS NOT NULL AS excepcion,
                               count(*) AS n, count(a.resuelta_en) AS cerradas,
                               coalesce(sum(extract(epoch FROM (a.resuelta_en - a.disparada_en)) / 60.0), 0) AS minutos
                        FROM alertas a
                                 JOIN base b ON b.id = a.cirugia_id
                                 LEFT JOIN reglas_seguridad r ON r.id = a.regla_seguridad_id
                        GROUP BY 1, 2, 3, 4, 5
                        """)
                .params(rango)
                .query((rs, n) -> new Grupo(rs.getString("severidad"), rs.getString("regla"), rs.getBoolean("bloqueante"),
                        rs.getBoolean("vigente"), rs.getBoolean("excepcion"), rs.getLong("n"), rs.getLong("cerradas"),
                        rs.getDouble("minutos")))
                .list();
        Map<String, Long> porSeveridad = new TreeMap<>();
        Map<String, Long> porRegla = new TreeMap<>();
        long total = 0;
        long vigentes = 0;
        long bloqueantes = 0;
        long excepciones = 0;
        long cerradas = 0;
        double minutos = 0;
        for (Grupo g : grupos) {
            total += g.n();
            porSeveridad.merge(g.severidad(), g.n(), Long::sum);
            porRegla.merge(g.regla(), g.n(), Long::sum);
            vigentes += g.vigente() ? g.n() : 0;
            bloqueantes += g.bloqueante() ? g.n() : 0;
            excepciones += g.excepcion() ? g.n() : 0;
            cerradas += g.cerradas();
            minutos += g.minutosCierre();
        }
        return new IndicadoresResponse.Alertas(total, vigentes, bloqueantes, excepciones, porSeveridad, porRegla,
                cerradas == 0 ? null : redondear(minutos / cerradas));
    }

    private IndicadoresResponse.Recuentos recuentos(Map<String, Object> rango) {
        Map<String, Long> porEstado = conteo(BASE + """
                SELECT r.estado_recuento AS clave, count(*) AS n
                FROM v_recuentos_cirugia r JOIN base b ON b.id = r.cirugia_id
                GROUP BY r.estado_recuento
                """, rango);
        return new IndicadoresResponse.Recuentos(porEstado.values().stream().mapToLong(Long::longValue).sum(), porEstado);
    }

    private IndicadoresResponse.Incidentes incidentes(Map<String, Object> rango) {
        record Grupo(String estado, String severidad, String categoria, long n) {
        }
        List<Grupo> grupos = jdbc.sql(BASE + """
                        SELECT i.estado::text AS estado, coalesce(i.severidad::text, 'SIN_SEVERIDAD') AS severidad,
                               coalesce(i.categoria, 'SIN_CATEGORIA') AS categoria, count(*) AS n
                        FROM incidentes i JOIN base b ON b.id = i.cirugia_id
                        GROUP BY 1, 2, 3
                        """)
                .params(rango)
                .query((rs, n) -> new Grupo(rs.getString("estado"), rs.getString("severidad"),
                        rs.getString("categoria"), rs.getLong("n")))
                .list();
        Map<String, Long> porEstado = new TreeMap<>();
        Map<String, Long> porSeveridad = new TreeMap<>();
        Map<String, Long> porCategoria = new TreeMap<>();
        long total = 0;
        for (Grupo g : grupos) {
            total += g.n();
            porEstado.merge(g.estado(), g.n(), Long::sum);
            porSeveridad.merge(g.severidad(), g.n(), Long::sum);
            porCategoria.merge(g.categoria(), g.n(), Long::sum);
        }
        return new IndicadoresResponse.Incidentes(total, porEstado, porSeveridad, porCategoria);
    }

    /** Trazabilidad de una cirugía: 4 consultas fijas (cabecera+conteos, hitos, participantes y eventos). */
    public TrazabilidadResponse trazabilidad(UUID cirugiaId) {
        record Cabecera(EstadoCirugia estado, LocalDateTime inicio, LocalDateTime fin,
                        TrazabilidadResponse.Conteos conteos) {
        }
        Cabecera cabecera = jdbc.sql("""
                        SELECT c.estado::text AS estado, c.inicio_programado, c.fin_programado,
                               (SELECT count(*) FROM alertas a WHERE a.cirugia_id = c.id) AS alertas,
                               (SELECT count(*) FROM alertas a WHERE a.cirugia_id = c.id
                                  AND a.estado IN ('ABIERTA', 'RECONOCIDA')) AS alertas_vigentes,
                               (SELECT count(*) FROM alertas a WHERE a.cirugia_id = c.id AND a.bloqueante) AS bloqueantes,
                               (SELECT count(*) FROM alertas a WHERE a.cirugia_id = c.id
                                  AND a.excepcion_autorizada_en IS NOT NULL) AS excepciones,
                               (SELECT count(*) FROM incidentes i WHERE i.cirugia_id = c.id) AS incidentes,
                               (SELECT count(*) FROM incidentes i WHERE i.cirugia_id = c.id
                                  AND i.estado IN ('ABIERTO', 'EN_PROGRESO')) AS incidentes_abiertos
                        FROM cirugias c
                        WHERE c.id = :id
                        """)
                .param("id", cirugiaId)
                .query((rs, n) -> new Cabecera(enumeracion(rs, "estado", EstadoCirugia.class),
                        fechaHora(rs, "inicio_programado"), fechaHora(rs, "fin_programado"),
                        new TrazabilidadResponse.Conteos(rs.getLong("alertas"), rs.getLong("alertas_vigentes"),
                                rs.getLong("bloqueantes"), rs.getLong("excepciones"), rs.getLong("incidentes"),
                                rs.getLong("incidentes_abiertos"))))
                .optional()
                .orElseThrow(() -> new RecursoNoEncontradoException("La cirugía " + cirugiaId + " no existe"));

        List<TrazabilidadResponse.Hito> hitos = jdbc.sql("""
                        SELECT tipo_hito::text AS tipo_hito, ocurrido_en
                        FROM hitos_cirugia
                        WHERE cirugia_id = :id AND anulado_en IS NULL
                        ORDER BY ocurrido_en
                        """)
                .param("id", cirugiaId)
                .query((rs, n) -> new TrazabilidadResponse.Hito(enumeracion(rs, "tipo_hito", TipoHito.class),
                        fechaHora(rs, "ocurrido_en")))
                .list();
        Map<TipoHito, LocalDateTime> porTipo = new EnumMap<>(TipoHito.class);
        hitos.forEach(h -> porTipo.put(h.tipo(), h.ocurridoEn()));
        Map<Tramo, Double> tiempos = new EnumMap<>(Tramo.class);
        for (Tramo tramo : Tramo.values()) {
            Double minutos = tramo.minutos(porTipo, cabecera.inicio());
            tiempos.put(tramo, minutos == null ? null : redondear(minutos));
        }
        return new TrazabilidadResponse(cirugiaId, cabecera.estado(), cabecera.inicio(), cabecera.fin(), hitos,
                tiempos, cabecera.conteos(), eventos.deCirugia(cirugiaId));
    }

    private Map<String, Long> conteo(String sql, Map<String, Object> rango) {
        Map<String, Long> resultado = new TreeMap<>();
        jdbc.sql(sql).params(rango).query(rs -> {
            resultado.put(rs.getString("clave"), rs.getLong("n"));
        });
        return resultado;
    }

    private static Double decimal(Object valor) {
        return valor == null ? null : redondear(((Number) valor).doubleValue());
    }

    private static double redondear(double valor) {
        return Math.round(valor * 100) / 100.0;
    }
}
