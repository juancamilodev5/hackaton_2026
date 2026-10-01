package com.hackaton.ulibre.auth;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.cors.origins: lista separada por comas (variable CORS_ORIGINS). */
@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> origins) {

    public CorsProperties {
        origins = origins == null ? List.of() : List.copyOf(origins);
    }
}
