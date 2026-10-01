package com.hackaton.ulibre.instrumental;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.ParticipanteVista;

/**
 * instrumentalCompleto y faltantes siguen el mismo criterio que fn_exigir_instrumental_completo:
 * sets requeridos e instrumentos directos requeridos con cantidad preparada menor que la requerida.
 * Los instrumentos dentro de un set se preparan con el set; no cuentan por separado.
 */
public record InstrumentalVista(
        boolean instrumentalCompleto,
        List<Faltante> faltantes,
        List<SetInstrumental> sets,
        List<Instrumento> instrumentosDirectos) {

    public record SetInstrumental(
            UUID id,
            String codigo,
            String nombre,
            boolean esRequerido,
            int cantidadRequerida,
            int cantidadPreparada,
            boolean preparado,
            ParticipanteVista preparadoPor,
            LocalDateTime preparadoEn,
            String notas,
            List<Instrumento> instrumentos) {
    }

    public record Instrumento(
            UUID id,
            String codigo,
            String nombre,
            boolean esRequerido,
            int cantidadRequerida,
            int cantidadPreparada,
            ParticipanteVista preparadoPor,
            LocalDateTime preparadoEn,
            String notas) {
    }

    /** tipo: SET o INSTRUMENTO. */
    public record Faltante(String tipo, String codigo, String nombre, int cantidadRequerida, int cantidadPreparada) {
    }
}
