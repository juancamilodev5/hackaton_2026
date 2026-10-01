package com.hackaton.ulibre;

import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

/**
 * Base de los tests de API: app completa en un puerto aleatorio, perfil dev (datos demo) y
 * PostgreSQL 18 desechable. Todas las clases comparten contexto y contenedor, así que cada test
 * crea sus propios datos y no depende del orden de ejecución entre clases.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Import(PostgresDePrueba.class)
public abstract class ApiTest {

    protected static final String ADMIN = "admin@demo.local";
    protected static final String JEFE = "enfermera.jefe@demo.local";
    protected static final String MEDICO = "alejandro.martinez@demo.local";
    protected static final String ANESTESIOLOGA = "laura.rodriguez@demo.local";
    protected static final String INSTRUMENTADORA = "maria.fernandez@demo.local";
    protected static final String OPERADOR = "carlos.perez@demo.local";
    protected static final String PACIENTE = "carlos.mendoza@demo.local";

    @LocalServerPort
    private int puerto;

    protected ClienteApi api;

    @BeforeEach
    void prepararCliente() {
        api = new ClienteApi(puerto);
    }

    /** Primer elemento de un arreglo JSON que cumple la condición (falla si no hay). */
    protected static JsonNode buscar(JsonNode arreglo, Predicate<JsonNode> condicion) {
        for (JsonNode elemento : arreglo) {
            if (condicion.test(elemento)) {
                return elemento;
            }
        }
        throw new AssertionError("No se encontró el elemento esperado en " + arreglo);
    }

    protected static Predicate<JsonNode> conCodigo(String codigo) {
        return n -> n.has("codigo") && codigo.equals(n.get("codigo").asString());
    }
}
