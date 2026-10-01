package com.hackaton.ulibre.eventos;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.hackaton.ulibre.cirugias.ParticipanteVista;

/** participante: con qué asignación (persona + rol en la cirugía) actuó, si participaba en ella. */
public record EventoVista(
        UUID id,
        String tipo,
        LocalDateTime ocurridoEn,
        UUID actorUsuarioId,
        String actor,
        ParticipanteVista participante,
        String tipoEntidad,
        UUID entidadId,
        @JsonRawValue String datos) {
}
