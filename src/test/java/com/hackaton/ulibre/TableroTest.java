package com.hackaton.ulibre;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Iteración 3: ejecución del tablero sobre la cirugía demo (bypass de Carlos Andrés Mendoza, mañana
 * 07:00). Solo el test de flujo la modifica; los demás leen cosas que ese flujo no cambia.
 */
class TableroTest extends ApiTest {

    @Test
    void flujoDelTablero() {
        String cirugia = cirugiaDemo();
        String base = "/api/cirugias/" + cirugia;
        String laura = asignacion(cirugia, "ANESTESIOLOGO");

        // --- Checklist: solo el operador del tablero lo inicia ----------------------------------
        api.post(ADMIN, base + "/checklist", null).esperar(422);   // TABLERO_OPERAR pero no es el operador
        JsonNode checklist = api.post(OPERADOR, base + "/checklist", null).esperar(201);
        assertThat(checklist.get("estado").asString()).isEqualTo("EN_PROGRESO");
        JsonNode fase1 = checklist.get("fases").get(0);
        assertThat(fase1.get("codigo").asString()).isEqualTo("PREANESTESIA");
        assertThat(fase1.get("estado").asString()).isEqualTo("EN_PROGRESO");
        api.post(OPERADOR, base + "/checklist", null).esperar(422);   // ya iniciado

        String identidad = buscar(fase1.get("items"), conCodigo("IDENTIDAD_PACIENTE")).get("id").asString();
        String procedimiento = buscar(fase1.get("items"), conCodigo("PROCEDIMIENTO_CONFIRMADO")).get("id").asString();

        // Confirmar antes de responder no se permite; responder con forma inválida → 422
        api.post(ANESTESIOLOGA, base + "/checklist/items/" + identidad + "/confirmaciones",
                Map.of("resultado", "CONFIRMADO")).esperar(422);
        api.put(OPERADOR, base + "/checklist/items/" + identidad + "/respuesta",
                Map.of("estado", "COMPLETADO", "respuesta", "sí")).esperar(422);

        // Registra el operador
        JsonNode respondido = api.put(OPERADOR, base + "/checklist/items/" + identidad + "/respuesta",
                Map.of("estado", "COMPLETADO", "respuesta", true)).esperar(200);
        JsonNode item = buscar(respondido.get("fases").get(0).get("items"), conCodigo("IDENTIDAD_PACIENTE"));
        assertThat(item.get("registradoPor").get("nombre").asString()).contains("Carlos");

        // Confirma la propia anestesióloga (rol del sistema MEDICO, sin TABLERO_OPERAR)
        JsonNode confirmado = api.post(ANESTESIOLOGA, base + "/checklist/items/" + identidad + "/confirmaciones",
                Map.of("resultado", "CONFIRMADO")).esperar(201);
        JsonNode confirmacion = buscar(confirmado.get("fases").get(0).get("items"), conCodigo("IDENTIDAD_PACIENTE"))
                .get("confirmaciones").get(0);
        assertThat(confirmacion.get("confirmadoPor").get("nombre").asString()).contains("Laura");
        assertThat(confirmacion.get("confirmadoPor").get("rol").get("codigo").asString()).isEqualTo("ANESTESIOLOGO");

        // El operador registra la confirmación de la anestesióloga; otra persona no puede hacerlo
        api.put(OPERADOR, base + "/checklist/items/" + procedimiento + "/respuesta",
                Map.of("estado", "COMPLETADO", "respuesta", true)).esperar(200);
        api.post(INSTRUMENTADORA, base + "/checklist/items/" + procedimiento + "/confirmaciones",
                Map.of("asignacionId", laura, "resultado", "CONFIRMADO")).esperar(422);
        api.post(OPERADOR, base + "/checklist/items/" + procedimiento + "/confirmaciones",
                Map.of("asignacionId", laura, "resultado", "CONFIRMADO")).esperar(201);
        // Quien no participa en la cirugía no confirma nada (el admin no está asignado)
        api.post(ADMIN, base + "/checklist/items/" + procedimiento + "/confirmaciones",
                Map.of("resultado", "CONFIRMADO")).esperar(422);

        // --- Recuentos: 10 + 5 agregadas, final 14 → DISCREPANCIA y alerta crítica bloqueante ---
        JsonNode gasas = api.post(OPERADOR, base + "/recuentos",
                Map.of("tipoRecuento", "GASAS", "cantidadInicial", 10)).esperar(201);
        String recuento = gasas.get("id").asString();
        api.post(ANESTESIOLOGA, base + "/recuentos/" + recuento + "/confirmaciones",
                Map.of("etapa", "INICIAL")).esperar(200);
        api.post(OPERADOR, base + "/recuentos/" + recuento + "/agregados", Map.of("cantidad", 5)).esperar(200);
        JsonNode final_ = api.put(OPERADOR, base + "/recuentos/" + recuento + "/final",
                Map.of("cantidadFinal", 14)).esperar(200);
        assertThat(final_.get("cantidadEsperada").asInt()).isEqualTo(15);
        assertThat(final_.get("estado").asString()).isEqualTo("DISCREPANCIA");
        JsonNode confirmadoFinal = api.post(INSTRUMENTADORA, base + "/recuentos/" + recuento + "/confirmaciones",
                Map.of("etapa", "FINAL")).esperar(200);
        assertThat(confirmadoFinal.get("confirmaciones").get("INICIAL")).hasSize(1);
        assertThat(confirmadoFinal.get("confirmaciones").get("FINAL")).hasSize(1);
        api.put(OPERADOR, base + "/recuentos/" + recuento + "/final", Map.of("cantidadFinal", 15)).esperar(422);

        // --- Alertas: la de recuento no se resuelve mientras la discrepancia siga ---------------
        JsonNode alerta = buscar(api.get(JEFE, base + "/alertas").esperar(200),
                a -> "RECUENTO_INCONSISTENTE".equals(a.get("regla").asString()) && a.get("vigente").asBoolean());
        assertThat(alerta.get("bloqueante").asBoolean()).isTrue();
        String alertaId = alerta.get("id").asString();
        api.post(OPERADOR, base + "/alertas/" + alertaId + "/reconocer", null).esperar(403);   // sin ALERTAS_GESTIONAR
        api.post(JEFE, base + "/alertas/" + alertaId + "/reconocer", null).esperar(200);
        api.post(JEFE, base + "/alertas/" + alertaId + "/resolver", Map.of("notas", "Ya está")).esperar(422);
        api.post(JEFE, base + "/alertas/" + alertaId + "/descartar", Map.of("notas", "No aplica")).esperar(422);
        JsonNode exceptuada = api.post(JEFE, base + "/alertas/" + alertaId + "/excepcion",
                Map.of("motivo", "Radiografía descarta cuerpo extraño")).esperar(200);
        assertThat(exceptuada.get("vigente").asBoolean()).isTrue();
        assertThat(exceptuada.get("excepcion").get("motivo").asString()).contains("Radiografía");

        // --- Hitos: registrar, anular y corregir; no se inicia la cirugía sin instrumental -----
        api.post(ADMIN, base + "/hitos", Map.of("tipoHito", "LLEGADA_PACIENTE")).esperar(422);
        String llegada = api.post(OPERADOR, base + "/hitos", Map.of("tipoHito", "LLEGADA_PACIENTE")).esperar(201)
                .get("id").asString();
        api.post(OPERADOR, base + "/hitos", Map.of("tipoHito", "LLEGADA_PACIENTE")).esperar(409);   // uno vigente por tipo
        api.post(OPERADOR, base + "/hitos/" + llegada + "/anular", Map.of("motivo", "Hora equivocada")).esperar(200);
        api.post(OPERADOR, base + "/hitos/" + llegada + "/anular", Map.of("motivo", "Otra vez")).esperar(422);
        api.post(OPERADOR, base + "/hitos", Map.of("tipoHito", "LLEGADA_PACIENTE", "corrigeHitoId", llegada))
                .esperar(201);
        assertThat(api.get(OPERADOR, base + "/hitos").esperar(200)).hasSize(1);
        assertThat(api.get(OPERADOR, base + "/hitos?incluirAnulados=true").esperar(200)).hasSize(2);
        api.post(OPERADOR, base + "/hitos", Map.of("tipoHito", "CIRUGIA_INICIADA")).esperar(422);   // faltan sets

        // --- Timeline: qué ocurrió y quién (y en qué rol) ---------------------------------------
        List<String> tipos = new ArrayList<>();
        api.get(ADMIN, base + "/eventos").esperar(200).forEach(e -> tipos.add(e.get("tipo").asString()));
        assertThat(tipos).contains("CHECKLIST_INICIADO", "ITEM_REGISTRADO", "ITEM_CONFIRMADO",
                "RECUENTO_FINAL_REGISTRADO", "ALERTA_RECONOCIDA", "EXCEPCION_AUTORIZADA", "HITO_ANULADO");
    }

    @Test
    void elPacienteSoloVeLoSuyo() {
        String cirugia = cirugiaDemo();
        JsonNode yo = api.get(PACIENTE, "/api/mi/paciente").esperar(200);
        assertThat(yo.get("apellidos").asString()).isEqualTo("Mendoza");
        assertThat(api.get(PACIENTE, "/api/mi/cirugias").esperar(200))
                .anySatisfy(c -> assertThat(c.get("id").asString()).isEqualTo(cirugia));
        api.get(PACIENTE, "/api/mi/cirugias/" + cirugia).esperar(200);
        api.get(PACIENTE, "/api/mi/cirugias/" + UUID.randomUUID()).esperar(404);
        // Sin permisos globales: no ve el tablero ni el panel
        api.get(PACIENTE, "/api/cirugias/" + cirugia + "/tablero").esperar(403);
        api.get(PACIENTE, "/api/cirugias").esperar(403);
        // Un usuario sin registro de paciente no tiene "mis datos"
        api.get(MEDICO, "/api/mi/paciente").esperar(404);
        assertThat(api.get(MEDICO, "/api/mi/cirugias").status()).isEqualTo(404);
    }

    @Test
    void indicadoresYTrazabilidad() {
        String cirugia = cirugiaDemo();
        api.get(ADMIN, "/api/indicadores").esperar(200);
        api.get(ADMIN, "/api/indicadores?desde=2026-02-01&hasta=2026-01-01").esperar(422);
        api.get(MEDICO, "/api/indicadores").esperar(403);
        api.get(ADMIN, "/api/cirugias/" + cirugia + "/trazabilidad").esperar(200);
        api.get(MEDICO, "/api/auditoria").esperar(403);
    }

    // ---------------------------------------------------------------------------------------------

    private String cirugiaDemo() {
        LocalDate manana = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(1);
        return buscar(api.get(ADMIN, "/api/cirugias?fecha=" + manana).esperar(200),
                c -> c.get("paciente").asString().contains("Mendoza")).get("id").asString();
    }

    /** Primera asignación vigente del rol clínico en la cirugía. */
    private String asignacion(String cirugia, String rol) {
        JsonNode equipo = api.get(ADMIN, "/api/cirugias/" + cirugia + "/tablero").esperar(200).get("equipo");
        return buscar(equipo.get("requerimientos"), r -> rol.equals(r.get("rol").get("codigo").asString()))
                .get("asignaciones").get(0).get("asignacionId").asString();
    }
}
