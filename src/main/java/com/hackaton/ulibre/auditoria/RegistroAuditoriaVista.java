package com.hackaton.ulibre.auditoria;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record RegistroAuditoriaVista(
        UUID id,
        LocalDateTime ocurridoEn,
        UUID usuarioId,
        String usuario,
        String tipoEntidad,
        UUID entidadId,
        String accion,
        @JsonRawValue String valoresAnteriores,
        @JsonRawValue String valoresNuevos) {
}
