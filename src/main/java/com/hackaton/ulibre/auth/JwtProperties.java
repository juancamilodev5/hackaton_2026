package com.hackaton.ulibre.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.jwt.*: el secreto llega por JWT_SECRET; la aplicación no arranca si falta o es corto. */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration expiracion) {

    public static final String EMISOR = "ulibre";
    private static final int BYTES_MINIMOS = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < BYTES_MINIMOS) {
            throw new IllegalStateException(
                    "JWT_SECRET (app.jwt.secret) es obligatorio y debe tener al menos " + BYTES_MINIMOS + " bytes");
        }
        if (expiracion == null) {
            expiracion = Duration.ofHours(8);
        }
    }

    public byte[] secretoEnBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
