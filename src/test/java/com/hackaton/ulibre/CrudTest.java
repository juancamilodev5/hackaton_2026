package com.hackaton.ulibre;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** CRUD: caso feliz y la traducción de cada familia de error (400, 401, 403, 404, 409, 422). */
class CrudTest extends ApiTest {

    @Test
    void catalogoCompletoConAuditoria() {
        JsonNode creada = api.post(ADMIN, "/api/especialidades",
                Map.of("codigo", "urologia_test", "nombre", "Urología")).esperar(201);
        String id = creada.get("id").asString();
        assertThat(creada.get("codigo").asString()).isEqualTo("UROLOGIA_TEST");
        assertThat(creada.get("nombre").asString()).isEqualTo("Urología");

        api.put(ADMIN, "/api/especialidades/" + id,
                Map.of("codigo", "UROLOGIA_TEST", "nombre", "Urología general")).esperar(200);
        api.delete(ADMIN, "/api/especialidades/" + id).esperar(204);
        assertThat(api.get(ADMIN, "/api/especialidades/" + id).esperar(200).get("activo").asBoolean()).isFalse();

        // Auditoría: alta, edición y desactivación con antes/después y quién
        JsonNode auditoria = api.get(ADMIN, "/api/auditoria?tipoEntidad=especialidades&entidadId=" + id)
                .esperar(200).get("contenido");
        assertThat(auditoria).hasSize(3);
        assertThat(auditoria.get(0).get("accion").asString()).isEqualTo("DESACTIVAR");
        assertThat(auditoria.get(0).get("usuario").asString()).isEqualTo("Administrador Demo");
        assertThat(auditoria.get(1).get("valoresAnteriores").get("nombre").asString()).isEqualTo("Urología");
        assertThat(auditoria.get(1).get("valoresNuevos").get("nombre").asString()).isEqualTo("Urología general");
    }

    @Test
    void erroresTraducidos() {
        // 400: validación con lista de campos
        JsonNode invalido = api.post(ADMIN, "/api/especialidades", Map.of("codigo", "con espacio", "nombre", ""))
                .esperar(400);
        assertThat(invalido.get("errores")).hasSize(2);

        // 401: sin token
        api.enviar(null, "GET", "/api/especialidades", null).esperar(401);

        // 403: el médico ve catálogos pero no los gestiona
        api.get(MEDICO, "/api/especialidades").esperar(200);
        api.post(MEDICO, "/api/especialidades", Map.of("codigo", "X", "nombre", "X")).esperar(403);

        // 404
        api.get(ADMIN, "/api/especialidades/00000000-0000-0000-0000-000000000000").esperar(404);

        // 409: código duplicado (UNIQUE de la base)
        JsonNode duplicado = api.post(ADMIN, "/api/especialidades",
                Map.of("codigo", "CIRUGIA_GENERAL", "nombre", "Otra")).esperar(409);
        assertThat(duplicado.get("codigoSql").asString()).isEqualTo("23505");

        // 422: quitar a un procedimiento una especialidad que usa una solicitud (FK compuesta)
        JsonNode procedimiento = buscar(api.get(ADMIN, "/api/procedimientos").esperar(200),
                conCodigo("BYPASS_CORONARIO"));
        JsonNode cardio = buscar(api.get(ADMIN, "/api/especialidades").esperar(200),
                conCodigo("CIRUGIA_CARDIOVASCULAR"));
        JsonNode fk = api.delete(ADMIN, "/api/procedimientos/" + procedimiento.get("id").asString()
                + "/especialidades/" + cardio.get("id").asString()).esperar(422);
        assertThat(fk.get("codigoSql").asString()).isEqualTo("23503");

        // 400 en parámetros (id que no es UUID) y tamaño de página recortado sin error
        api.get(ADMIN, "/api/usuarios/no-es-uuid").esperar(400);
        assertThat(api.get(ADMIN, "/api/usuarios?tamano=500").esperar(200).get("tamano").asInt()).isEqualTo(100);
    }

    @Test
    void plantillaPublicadaNoSeEdita() {
        JsonNode plantilla = buscar(api.get(ADMIN, "/api/plantillas-checklist").esperar(200),
                conCodigo("SEGURIDAD_QUIRURGICA_ESTANDAR"));
        // El trigger fn_proteger_plantilla rechaza editar una plantilla PUBLICADA → 422
        api.put(ADMIN, "/api/plantillas-checklist/" + plantilla.get("id").asString(),
                Map.of("codigo", "SEGURIDAD_QUIRURGICA_ESTANDAR", "nombre", "Cambio")).esperar(422);
    }
}
