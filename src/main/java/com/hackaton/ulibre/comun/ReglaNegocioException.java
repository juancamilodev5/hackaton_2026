package com.hackaton.ulibre.comun;

/**
 * Regla que se comprueba en Java porque la base no la cubre (p. ej. transiciones de estado de una
 * solicitud). Se responde 422, igual que las reglas que sí viven en la base.
 */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
