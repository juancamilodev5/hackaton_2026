package com.hackaton.ulibre.indicadores;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.EstadoCirugia;
import com.hackaton.ulibre.eventos.EventoVista;
import com.hackaton.ulibre.tablero.TipoHito;

/** Trazabilidad de una cirugía: hitos vigentes, tiempos medidos, conteos y timeline de eventos. */
public record TrazabilidadResponse(
        UUID cirugiaId,
        EstadoCirugia estado,
        LocalDateTime inicioProgramado,
        LocalDateTime finProgramado,
        List<Hito> hitos,
        /** Minutos por tramo; null si falta alguno de sus hitos. */
        Map<Tramo, Double> tiemposMinutos,
        Conteos conteos,
        List<EventoVista> eventos) {

    public record Hito(TipoHito tipo, LocalDateTime ocurridoEn) {
    }

    public record Conteos(long alertas, long alertasVigentes, long alertasBloqueantes, long excepcionesAutorizadas,
                          long incidentes, long incidentesAbiertos) {
    }
}
