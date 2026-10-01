package com.hackaton.ulibre.checklist;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.hackaton.ulibre.comun.ReglaNegocioException;
import tools.jackson.databind.JsonNode;

/**
 * Valida la FORMA de la respuesta de un ítem según su tipo_respuesta. La respuesta se sigue
 * guardando tal cual en jsonb (no se convierte en columnas). Un error de forma responde 422.
 * Se aceptan los nombres en español de la plantilla y sus equivalentes en inglés; un tipo
 * desconocido admite cualquier JSON (la plantilla es configurable).
 */
final class ValidadorRespuesta {

    private ValidadorRespuesta() {
    }

    /** @param config config_validacion del ítem de plantilla de origen (puede ser null) */
    static void validar(String tipoRespuesta, JsonNode respuesta, JsonNode config) {
        String tipo = tipoRespuesta == null ? "" : tipoRespuesta.toUpperCase(Locale.ROOT);
        switch (tipo) {
            case "CONFIRMACION", "CONFIRMATION", "BOOLEANO", "BOOLEAN" -> exigir(respuesta.isBoolean(),
                    "La respuesta de un ítem de tipo " + tipo + " debe ser true o false");
            case "TEXTO", "TEXT" -> exigir(respuesta.isString() && !respuesta.stringValue().isBlank(),
                    "La respuesta de un ítem de tipo " + tipo + " debe ser un texto no vacío");
            case "NUMERO", "NUMBER" -> numero(respuesta, config);
            case "FECHA", "DATE" -> fecha(respuesta, tipo, LocalDate::parse, "yyyy-MM-dd");
            case "HORA", "TIME" -> fecha(respuesta, tipo, LocalTime::parse, "HH:mm o HH:mm:ss");
            case "FECHA_HORA", "DATETIME" -> fecha(respuesta, tipo, LocalDateTime::parse, "yyyy-MM-ddTHH:mm[:ss]");
            case "SELECCION", "SELECT" -> {
                exigir(respuesta.isString(), "La respuesta de un ítem de tipo " + tipo + " debe ser un texto");
                Set<String> opciones = opciones(config);
                exigir(opciones == null || opciones.contains(respuesta.stringValue()),
                        "La respuesta no es una de las opciones permitidas: " + opciones);
            }
            case "SELECCION_MULTIPLE", "MULTI_SELECT" -> {
                exigir(respuesta.isArray(), "La respuesta de un ítem de tipo " + tipo + " debe ser un arreglo");
                Set<String> opciones = opciones(config);
                for (int i = 0; i < respuesta.size(); i++) {
                    JsonNode valor = respuesta.get(i);
                    exigir(valor.isString(), "Cada valor de un ítem de tipo " + tipo + " debe ser un texto");
                    exigir(opciones == null || opciones.contains(valor.stringValue()),
                            "«" + valor.stringValue() + "» no es una de las opciones permitidas: " + opciones);
                }
            }
            default -> {
                // Tipo no reconocido: cualquier JSON
            }
        }
    }

    private static void numero(JsonNode respuesta, JsonNode config) {
        exigir(respuesta.isNumber(), "La respuesta de un ítem numérico debe ser un número");
        BigDecimal valor = respuesta.asDecimal();
        if (config != null && config.get("min") != null && config.get("min").isNumber()) {
            exigir(valor.compareTo(config.get("min").asDecimal()) >= 0,
                    "La respuesta debe ser mayor o igual que " + config.get("min").asDecimal());
        }
        if (config != null && config.get("max") != null && config.get("max").isNumber()) {
            exigir(valor.compareTo(config.get("max").asDecimal()) <= 0,
                    "La respuesta debe ser menor o igual que " + config.get("max").asDecimal());
        }
    }

    private interface Analizador {
        Object analizar(CharSequence texto);
    }

    private static void fecha(JsonNode respuesta, String tipo, Analizador analizador, String formato) {
        exigir(respuesta.isString(), "La respuesta de un ítem de tipo " + tipo + " debe ser un texto " + formato);
        try {
            analizador.analizar(respuesta.stringValue());
        } catch (DateTimeParseException ex) {
            throw new ReglaNegocioException("La respuesta de un ítem de tipo " + tipo + " debe tener el formato " + formato);
        }
    }

    /** config_validacion.opciones si es un arreglo de textos; null si no restringe. */
    private static Set<String> opciones(JsonNode config) {
        JsonNode opciones = config == null ? null : config.get("opciones");
        if (opciones == null || !opciones.isArray()) {
            return null;
        }
        Set<String> valores = new HashSet<>();
        for (int i = 0; i < opciones.size(); i++) {
            if (opciones.get(i).isString()) {
                valores.add(opciones.get(i).stringValue());
            }
        }
        return valores;
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new ReglaNegocioException(mensaje);
        }
    }
}
