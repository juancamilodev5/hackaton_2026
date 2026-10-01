package com.hackaton.ulibre.auth;

/** Mensaje genérico a propósito: no revela si el correo existe ni el estado de la cuenta. */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Credenciales inválidas");
    }
}
