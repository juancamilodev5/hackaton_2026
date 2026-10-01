package com.hackaton.ulibre.indicadores;

import java.time.LocalDate;
import java.util.Map;

/** Indicadores de calidad de las cirugías cuyo inicio programado cae en [desde, hasta]. Solo datos reales. */
public record IndicadoresResponse(
        LocalDate desde,
        LocalDate hasta,
        Cirugias cirugias,
        Map<Tramo, Medida> tiempos,
        Checklist checklist,
        Alertas alertas,
        Recuentos recuentos,
        Incidentes incidentes) {

    public record Cirugias(long total, Map<String, Long> porEstado, long completadas, long canceladas,
                           long suspendidas) {
    }

    /** Minutos; null cuando no hay ninguna cirugía con los dos hitos del tramo (n = 0). */
    public record Medida(Double promedio, Double minimo, Double maximo, long n) {
    }

    /** adherencia: % de cirugías COMPLETADAS cuyo checklist quedó COMPLETADO (null si no hay completadas). */
    public record Checklist(long cirugiasConChecklist, long checklistsCompletados, long cirugiasCompletadas,
                            Double adherenciaPorcentaje, Map<String, Long> itemsPorEstado) {
    }

    /** tiempoMedioResolucionMinutos: de disparada a resuelta/descartada. */
    public record Alertas(long total, long vigentes, long bloqueantes, long excepcionesAutorizadas,
                          Map<String, Long> porSeveridad, Map<String, Long> porRegla,
                          Double tiempoMedioResolucionMinutos) {
    }

    public record Recuentos(long total, Map<String, Long> porEstado) {
    }

    public record Incidentes(long total, Map<String, Long> porEstado, Map<String, Long> porSeveridad,
                             Map<String, Long> porCategoria) {
    }
}
