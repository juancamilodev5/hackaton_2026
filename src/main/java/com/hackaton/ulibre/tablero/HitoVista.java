package com.hackaton.ulibre.tablero;

import java.time.LocalDateTime;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.ParticipanteVista;

public record HitoVista(
        UUID id,
        TipoHito tipo,
        LocalDateTime ocurridoEn,
        ParticipanteVista registradoPor,
        String notas,
        UUID corrigeHitoId) {
}
