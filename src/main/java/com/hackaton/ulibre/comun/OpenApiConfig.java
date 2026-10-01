package com.hackaton.ulibre.comun;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Ulibre — Plataforma de seguridad quirúrgica", version = "v1"),
        security = @SecurityRequirement(name = OpenApiConfig.ESQUEMA_JWT))
@SecurityScheme(
        name = OpenApiConfig.ESQUEMA_JWT,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Token obtenido en POST /api/auth/login")
public class OpenApiConfig {

    public static final String ESQUEMA_JWT = "bearer-jwt";
}
