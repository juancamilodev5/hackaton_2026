package com.hackaton.ulibre.tablero;

import java.time.LocalDateTime;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.ParticipanteVista;

/** Hito con su historia: un hito anulado se conserva (nunca se borra) y su corrección lo referencia. */
public record HitoDetalle(
        UUID id,
        TipoHito tipo,
        LocalDateTime ocurridoEn,
        ParticipanteVista registradoPor,
        String notas,
        UUID corrigeHitoId,
        boolean vigente,
        LocalDateTime anuladoEn,
        String anuladoPor,
        String motivoAnulacion,
        LocalDateTime creadoEn) {
}
