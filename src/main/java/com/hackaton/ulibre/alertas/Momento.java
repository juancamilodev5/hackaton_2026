package com.hackaton.ulibre.alertas;

/**
 * Punto del flujo en el que se evalúan las reglas. Algunas reglas solo aplican desde cierto punto
 * (p. ej. el antibiótico se exige antes de la incisión): ver {@link MotorReglas}.
 *
 * @param codigoFase fase que se intenta cerrar (solo para {@link Tipo#CIERRE_FASE})
 */
public record Momento(Tipo tipo, String codigoFase) {

    public enum Tipo {
        /** Reevaluación tras registrar algo o a pedido; no adelanta ninguna puerta. */
        EVALUACION,
        /** Antes de cerrar la fase {@code codigoFase}. */
        CIERRE_FASE,
        /** Antes de pasar la cirugía a EN_CIRUGIA o registrar el hito CIRUGIA_INICIADA. */
        INICIO_CIRUGIA
    }

    public static Momento evaluacion() {
        return new Momento(Tipo.EVALUACION, null);
    }

    public static Momento cierreFase(String codigoFase) {
        return new Momento(Tipo.CIERRE_FASE, codigoFase);
    }

    public static Momento inicioCirugia() {
        return new Momento(Tipo.INICIO_CIRUGIA, null);
    }
}
