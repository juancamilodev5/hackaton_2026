package com.hackaton.ulibre.indicadores;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import com.hackaton.ulibre.tablero.TipoHito;

/**
 * Tiempos que se miden entre hitos vigentes. Único lugar donde se definen: de aquí salen tanto el
 * SQL agregado de los indicadores como el cálculo por cirugía de la trazabilidad.
 *
 * @param desde hito inicial; null = inicio programado de la cirugía (para el retraso)
 */
public enum Tramo {
    DURACION_QUIRURGICA(TipoHito.CIRUGIA_INICIADA, TipoHito.CIRUGIA_FINALIZADA),
    ANESTESIA(TipoHito.ANESTESIA_INICIADA, TipoHito.SALIDA_COMPLETADA),
    ESTANCIA_QUIROFANO(TipoHito.INGRESO_QUIROFANO, TipoHito.TRASLADO_A_RECUPERACION),
    RETRASO_INICIO(null, TipoHito.INGRESO_QUIROFANO);

    private final TipoHito desde;
    private final TipoHito hasta;

    Tramo(TipoHito desde, TipoHito hasta) {
        this.desde = desde;
        this.hasta = hasta;
    }

    /** Expresión SQL (minutos) sobre columnas pivote h_<TIPO_HITO> e inicio_programado. */
    String expresionSql() {
        String inicio = desde == null ? "inicio_programado" : "h_" + desde.name().toLowerCase();
        return "extract(epoch FROM (h_" + hasta.name().toLowerCase() + " - " + inicio + ")) / 60.0";
    }

    /** Minutos para una cirugía, o null si falta alguno de los dos extremos. */
    Double minutos(Map<TipoHito, LocalDateTime> hitos, LocalDateTime inicioProgramado) {
        LocalDateTime inicio = desde == null ? inicioProgramado : hitos.get(desde);
        LocalDateTime fin = hitos.get(hasta);
        if (inicio == null || fin == null) {
            return null;
        }
        return Duration.between(inicio, fin).toSeconds() / 60.0;
    }

    static String columnasPivoteSql() {
        StringBuilder sql = new StringBuilder();
        for (TipoHito tipo : TipoHito.values()) {
            sql.append(", max(h.ocurrido_en) FILTER (WHERE h.tipo_hito = '").append(tipo.name())
                    .append("') AS h_").append(tipo.name().toLowerCase());
        }
        return sql.toString();
    }
}
