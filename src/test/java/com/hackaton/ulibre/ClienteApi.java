package com.hackaton.ulibre;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente HTTP real contra la app levantada en un puerto aleatorio: pasa por el filtro JWT, los
 * @PreAuthorize, el ManejadorErrores y las transacciones de verdad.
 */
public final class ClienteApi {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient http = HttpClient.newHttpClient();
    private final String base;
    private final Map<String, String> tokens = new HashMap<>();

    public ClienteApi(int puerto) {
        this.base = "http://localhost:" + puerto;
    }

    /** Token de un usuario demo (contraseña demo1234), cacheado. */
    public String token(String correo) {
        return tokens.computeIfAbsent(correo, c -> {
            Respuesta r = enviar(null, "POST", "/api/auth/login", Map.of("correo", c, "contrasena", "demo1234"));
            r.esperar(200);
            return r.cuerpo().get("token").asString();
        });
    }

    public Respuesta get(String correo, String ruta) {
        return enviar(token(correo), "GET", ruta, null);
    }

    public Respuesta post(String correo, String ruta, Object cuerpo) {
        return enviar(token(correo), "POST", ruta, cuerpo);
    }

    public Respuesta put(String correo, String ruta, Object cuerpo) {
        return enviar(token(correo), "PUT", ruta, cuerpo);
    }

    public Respuesta patch(String correo, String ruta, Object cuerpo) {
        return enviar(token(correo), "PATCH", ruta, cuerpo);
    }

    public Respuesta delete(String correo, String ruta) {
        return enviar(token(correo), "DELETE", ruta, null);
    }

    public Respuesta enviar(String token, String metodo, String ruta, Object cuerpo) {
        try {
            HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create(base + ruta));
            if (token != null) {
                peticion.header("Authorization", "Bearer " + token);
            }
            if (cuerpo != null) {
                peticion.header("Content-Type", "application/json");
                peticion.method(metodo, HttpRequest.BodyPublishers.ofString(
                        cuerpo instanceof String texto ? texto : JSON.writeValueAsString(cuerpo), StandardCharsets.UTF_8));
            } else {
                peticion.method(metodo, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> respuesta = http.send(peticion.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String texto = respuesta.body();
            JsonNode nodo = texto == null || texto.isBlank() ? JSON.nullNode() : JSON.readTree(texto);
            return new Respuesta(metodo + " " + ruta, respuesta.statusCode(), nodo);
        } catch (Exception ex) {
            throw new IllegalStateException("Falló la petición " + metodo + " " + ruta, ex);
        }
    }

    public record Respuesta(String peticion, int status, JsonNode cuerpo) {

        /** Comprueba el status (mostrando el cuerpo si no coincide) y devuelve el cuerpo. */
        public JsonNode esperar(int esperado) {
            assertThat(status).as("%s → %s", peticion, cuerpo).isEqualTo(esperado);
            return cuerpo;
        }

        public String id() {
            return cuerpo.get("id").asString();
        }

        /** "detail" del problem+json. */
        public String detalle() {
            JsonNode detalle = cuerpo.get("detail");
            return detalle == null ? "" : detalle.asString();
        }
    }
}
