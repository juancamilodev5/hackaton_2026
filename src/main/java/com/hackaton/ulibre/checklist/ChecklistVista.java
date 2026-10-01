package com.hackaton.ulibre.checklist;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.hackaton.ulibre.catalogos.RolClinicoVista;
import com.hackaton.ulibre.cirugias.ParticipanteVista;

public record ChecklistVista(
        UUID id,
        Plantilla plantilla,
        EstadoChecklist estado,
        LocalDateTime iniciadoEn,
        LocalDateTime completadoEn,
        List<Fase> fases) {

    /** Plantilla de origen tal como se copió (código, nombre y versión del snapshot). */
    public record Plantilla(String codigo, String nombre, int version) {
    }

    public record Fase(
            UUID id,
            String codigo,
            String nombre,
            int orden,
            EstadoFaseChecklist estado,
            LocalDateTime iniciadaEn,
            ParticipanteVista cerradaPor,
            LocalDateTime cerradaEn,
            String notas,
            List<Item> items) {
    }

    public record Item(
            UUID id,
            String codigo,
            String etiqueta,
            String descripcion,
            String tipoRespuesta,
            boolean obligatorio,
            boolean bloqueante,
            RolClinicoVista rolResponsable,
            EstadoItemChecklist estado,
            /** JSON tal cual está en la base; su forma depende de tipoRespuesta. */
            @JsonRawValue String respuesta,
            ParticipanteVista registradoPor,
            LocalDateTime registradoEn,
            String notas,
            List<Confirmacion> confirmaciones) {
    }

    /** confirmadoPor.rol es la calidad en que la persona participa en esta cirugía. */
    public record Confirmacion(
            UUID id,
            ParticipanteVista confirmadoPor,
            ResultadoConfirmacion resultado,
            LocalDateTime confirmadoEn,
            String notas) {
    }
}
