package com.hackaton.ulibre;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL 18 desechable para los tests (requiere Docker). Flyway aplica las migraciones reales
 * (V1..V4 y los datos demo V100 del perfil dev), así que las reglas de la base —triggers,
 * constraints, funciones— se prueban de verdad. El contenedor se comparte entre las clases de
 * test del mismo contexto de Spring y no toca la base de desarrollo.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresDePrueba {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18");
    }
}
