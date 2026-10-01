package com.hackaton.ulibre.tablero;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.ParticipanteVista;

public record RecuentoVista(
        UUID id,
        TipoRecuento tipo,
        String descripcion,
        UUID instrumentoCirugiaId,
        int cantidadInicial,
        int cantidadAgregada,
        Integer cantidadFinal,
        /** inicial + agregada, calculada por v_recuentos_cirugia. */
        int cantidadEsperada,
        EstadoRecuento estado,
        ParticipanteVista registradoInicialPor,
        LocalDateTime registradoInicialEn,
        ParticipanteVista registradoFinalPor,
        LocalDateTime registradoFinalEn,
        String notas,
        /** Siempre trae las dos etapas (INICIAL y FINAL), vacías si nadie ha confirmado. */
        Map<EtapaRecuento, List<ConfirmacionConteo>> confirmaciones) {

    public record ConfirmacionConteo(
            UUID id,
            ParticipanteVista confirmadoPor,
            LocalDateTime confirmadoEn,
            String notas) {
    }
}
