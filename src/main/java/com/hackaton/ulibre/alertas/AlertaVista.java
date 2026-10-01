package com.hackaton.ulibre.alertas;

import java.time.LocalDateTime;
import java.util.UUID;

public record AlertaVista(
        UUID id,
        /** Código de la regla de seguridad que la generó, o null si es manual. */
        String regla,
        String tipoOrigen,
        UUID origenId,
        SeveridadAlerta severidad,
        boolean bloqueante,
        EstadoAlerta estado,
        boolean vigente,
        String titulo,
        String mensaje,
        LocalDateTime disparadaEn,
        String reconocidaPor,
        LocalDateTime reconocidaEn,
        String resueltaPor,
        LocalDateTime resueltaEn,
        String notasResolucion,
        /** null si no hay excepción autorizada (regla 16). */
        Excepcion excepcion) {

    public record Excepcion(String autorizadaPor, LocalDateTime autorizadaEn, String motivo) {
    }
}
