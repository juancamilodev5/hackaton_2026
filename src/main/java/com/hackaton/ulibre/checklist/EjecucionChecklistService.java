package com.hackaton.ulibre.checklist;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.hackaton.ulibre.alertas.Momento;
import com.hackaton.ulibre.alertas.MotorReglas;
import com.hackaton.ulibre.cirugias.CirugiasConsultas;
import com.hackaton.ulibre.cirugias.EstadoCirugia;
import com.hackaton.ulibre.cirugias.ParticipacionCirugia;
import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.ReglaNegocioException;
import com.hackaton.ulibre.comun.Textos;
import com.hackaton.ulibre.eventos.EventosCirugia;
import com.hackaton.ulibre.eventos.TipoEvento;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ejecución del checklist de una cirugía. La copia de la plantilla la hace fn_iniciar_checklist; el
 * operador del tablero, el rol de quien confirma, el personal vigente y el bloqueo de fases con
 * alertas los garantizan los triggers. Aquí solo va el flujo que la base no cubre: fases en orden,
 * qué se puede responder o confirmar en cada momento y la forma de la respuesta.
 *
 * <p>Los ítems obligatorios pendientes no se comprueban en Java al cerrar una fase: los detecta la
 * regla FASE_OBLIGATORIA_INCOMPLETA (MotorReglas) y, si está configurada como bloqueante, el trigger
 * fn_validar_cierre_fase rechaza el cierre.
 */
@Service
public class EjecucionChecklistService {

    /** Una cirugía en estos estados ya no admite registros en su checklist. */
    private static final Set<EstadoCirugia> CERRADAS =
            EnumSet.of(EstadoCirugia.CANCELADA, EstadoCirugia.SUSPENDIDA, EstadoCirugia.COMPLETADA);

    /** Hito que se registra solo al cerrar cada fase de la plantilla estándar. */
    private static final Map<String, String> HITO_POR_FASE = Map.of(
            "PREANESTESIA", "PREANESTESIA_COMPLETADA",
            "PREINCISION", "PREINCISION_COMPLETADA",
            "SALIDA", "SALIDA_COMPLETADA");

    private final ChecklistCirugiaRepository checklists;
    private final FaseChecklistCirugiaRepository fases;
    private final ItemChecklistCirugiaRepository items;
    private final ConfirmacionItemChecklistRepository confirmaciones;
    private final ChecklistConsultas consultas;
    private final CirugiasConsultas cirugias;
    private final ParticipacionCirugia participacion;
    private final EventosCirugia eventos;
    private final MotorReglas motor;
    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public EjecucionChecklistService(ChecklistCirugiaRepository checklists, FaseChecklistCirugiaRepository fases,
            ItemChecklistCirugiaRepository items, ConfirmacionItemChecklistRepository confirmaciones,
            ChecklistConsultas consultas, CirugiasConsultas cirugias, ParticipacionCirugia participacion,
            EventosCirugia eventos, MotorReglas motor, JdbcClient jdbc, JsonMapper json, Clock clock) {
        this.checklists = checklists;
        this.fases = fases;
        this.items = items;
        this.confirmaciones = confirmaciones;
        this.consultas = consultas;
        this.cirugias = cirugias;
        this.participacion = participacion;
        this.eventos = eventos;
        this.motor = motor;
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ChecklistVista obtener(UUID cirugiaId) {
        participacion.exigirCirugia(cirugiaId);
        return vista(cirugiaId);
    }

    /** Copia la plantilla (si aún no hay checklist) y lo inicia: checklist EN_PROGRESO y primera fase abierta. */
    @Transactional
    public ChecklistVista iniciar(UUID cirugiaId, UUID plantillaId) {
        exigirAbierta(cirugiaId);
        UUID operador = participacion.exigirOperador(cirugiaId);

        if (checklists.findFirstByCirugiaIdOrderByCreadoEnDesc(cirugiaId).isEmpty()) {
            jdbc.sql("SELECT fn_iniciar_checklist(:cirugiaId, CAST(:plantillaId AS uuid))")
                    .param("cirugiaId", cirugiaId)
                    .param("plantillaId", plantillaId)
                    .query(UUID.class)
                    .single();
        }
        ChecklistCirugia checklist = checklists.findFirstByCirugiaIdOrderByCreadoEnDesc(cirugiaId).orElseThrow();
        if (checklist.getEstado() != EstadoChecklist.PENDIENTE || checklist.getIniciadoEn() != null) {
            throw new ReglaNegocioException("El checklist de esta cirugía ya fue iniciado");
        }

        LocalDateTime ahora = LocalDateTime.now(clock);
        checklist.setEstado(EstadoChecklist.EN_PROGRESO);
        checklist.setIniciadoEn(ahora);
        checklists.saveAndFlush(checklist);   // tg_checklists_inicio_tablero: exige operador

        fases.findAllByChecklistCirugiaIdOrderByOrden(checklist.getId()).stream().findFirst().ifPresent(primera -> {
            primera.setEstado(EstadoFaseChecklist.EN_PROGRESO);
            primera.setIniciadaEn(ahora);
            fases.saveAndFlush(primera);
        });

        eventos.registrar(cirugiaId, TipoEvento.CHECKLIST_INICIADO, operador, "checklists_cirugia", checklist.getId(),
                Map.of("plantilla", checklist.getCodigoPlantilla(), "version", checklist.getVersionPlantilla()));
        motor.evaluarAlConfirmar(cirugiaId);
        return vista(cirugiaId);
    }

    /** Registra (o corrige mientras la fase esté abierta) la respuesta de un ítem de la fase actual. */
    @Transactional
    public ChecklistVista responder(UUID cirugiaId, UUID itemId, RespuestaItemRequest datos) {
        exigirAbierta(cirugiaId);
        UUID operador = participacion.exigirOperador(cirugiaId);
        ItemChecklistCirugia item = item(cirugiaId, itemId);
        FaseChecklistCirugia fase = exigirFaseActual(cirugiaId, item.getFaseCirugiaId());

        if (datos.estado() == EstadoItemChecklist.PENDIENTE) {
            throw new ReglaNegocioException("El estado de la respuesta debe ser COMPLETADO, ADVERTENCIA, FALLIDO o NO_APLICA");
        }
        JsonNode respuesta = datos.respuesta() == null || datos.respuesta().isNull() ? null : datos.respuesta();
        if (respuesta == null && datos.estado() != EstadoItemChecklist.NO_APLICA) {
            throw new ReglaNegocioException("La respuesta es obligatoria salvo cuando el ítem no aplica");
        }
        if (respuesta != null) {
            ValidadorRespuesta.validar(item.getTipoRespuesta(), respuesta, configValidacion(item));
        }

        LocalDateTime ahora = LocalDateTime.now(clock);
        if (fase.getEstado() == EstadoFaseChecklist.PENDIENTE) {
            fase.setEstado(EstadoFaseChecklist.EN_PROGRESO);
            fase.setIniciadaEn(ahora);
            fases.saveAndFlush(fase);
        }
        item.setEstado(datos.estado());
        item.setRespuesta(respuesta == null ? null : respuesta.toString());
        item.setRegistradoPorAsignacionId(operador);
        item.setRegistradoEn(ahora);
        item.setNotas(Textos.limpiar(datos.notas()));
        items.saveAndFlush(item);

        eventos.registrar(cirugiaId, TipoEvento.ITEM_REGISTRADO, operador, "items_checklist_cirugia", item.getId(),
                Map.of("codigo", item.getCodigoItem(), "estado", datos.estado().name()));
        motor.evaluarAlConfirmar(cirugiaId);
        return vista(cirugiaId);
    }

    /**
     * Confirmación clínica de un ítem por una persona en el rol con que participa. La registra el
     * operador del tablero en nombre de otro, o la propia persona. El rol exigido por el ítem, la
     * vigencia de la asignación y el duplicado los valida la base.
     */
    @Transactional
    public ChecklistVista confirmar(UUID cirugiaId, UUID itemId, ConfirmacionItemRequest datos) {
        exigirAbierta(cirugiaId);
        Optional<UUID> propia = participacion.asignacionVigente(cirugiaId);
        Optional<UUID> operador = operadorActual(cirugiaId);
        UUID confirmador = datos.asignacionId() != null ? datos.asignacionId()
                : propia.orElseThrow(() -> new ReglaNegocioException(
                        "Indique la asignación de quien confirma: usted no tiene una asignación vigente en esta cirugía"));
        if (operador.isEmpty() && !propia.map(confirmador::equals).orElse(false)) {
            throw new ReglaNegocioException(
                    "Solo el operador del tablero puede registrar confirmaciones de otra persona");
        }

        ItemChecklistCirugia item = item(cirugiaId, itemId);
        if (item.getEstado() == EstadoItemChecklist.PENDIENTE) {
            throw new ReglaNegocioException("Registre la respuesta del ítem antes de confirmarlo");
        }
        FaseChecklistCirugia fase = fases.findById(item.getFaseCirugiaId()).orElseThrow();
        if (fase.getEstado() == EstadoFaseChecklist.COMPLETADA) {
            throw new ReglaNegocioException("La fase «" + fase.getNombreFase() + "» ya está cerrada");
        }

        ConfirmacionItemChecklist confirmacion = new ConfirmacionItemChecklist();
        confirmacion.setCirugiaId(cirugiaId);
        confirmacion.setItemChecklistCirugiaId(itemId);
        confirmacion.setAsignacionPersonalId(confirmador);
        confirmacion.setResultado(datos.resultado());
        confirmacion.setConfirmadoEn(LocalDateTime.now(clock));
        confirmacion.setNotas(Textos.limpiar(datos.notas()));
        confirmaciones.saveAndFlush(confirmacion);   // triggers de rol y vigencia; uq_confirmacion_item_persona

        UUID registradoPor = operador.orElseGet(propia::orElseThrow);
        eventos.registrar(cirugiaId, TipoEvento.ITEM_CONFIRMADO, confirmador, "confirmaciones_item_checklist",
                confirmacion.getId(), Map.of("codigo", item.getCodigoItem(), "resultado", datos.resultado().name(),
                        "registradoPor", registradoPor));
        motor.evaluarAlConfirmar(cirugiaId);
        return vista(cirugiaId);
    }

    /**
     * Cierra la fase actual. Las reglas se evalúan ANTES de escribir nada (en su propia transacción,
     * para que sus alertas queden guardadas); luego el trigger fn_validar_cierre_fase decide.
     */
    @Transactional
    public ChecklistVista cerrarFase(UUID cirugiaId, UUID faseId, String notas) {
        exigirAbierta(cirugiaId);
        UUID operador = participacion.exigirOperador(cirugiaId);
        FaseChecklistCirugia fase = fases.findById(faseId)
                .filter(f -> f.getCirugiaId().equals(cirugiaId))
                .orElseThrow(() -> new RecursoNoEncontradoException("La fase " + faseId + " no es de esta cirugía"));
        exigirFaseActual(cirugiaId, faseId);

        motor.evaluar(cirugiaId, Momento.cierreFase(fase.getCodigoFase()));

        LocalDateTime ahora = LocalDateTime.now(clock);
        fase.setEstado(EstadoFaseChecklist.COMPLETADA);
        fase.setCerradaPorAsignacionId(operador);
        fase.setCerradaEn(ahora);
        fase.setNotas(Textos.limpiar(notas));
        fases.saveAndFlush(fase);   // fn_validar_cierre_fase: alertas bloqueantes vigentes → 422

        ChecklistCirugia checklist = checklists.findById(fase.getChecklistCirugiaId()).orElseThrow();
        Optional<FaseChecklistCirugia> siguiente = fases.findAllByChecklistCirugiaIdOrderByOrden(checklist.getId())
                .stream()
                .filter(f -> f.getEstado() != EstadoFaseChecklist.COMPLETADA)
                .findFirst();
        if (siguiente.isPresent()) {
            siguiente.get().setEstado(EstadoFaseChecklist.EN_PROGRESO);
            siguiente.get().setIniciadaEn(ahora);
            fases.saveAndFlush(siguiente.get());
        } else {
            checklist.setEstado(EstadoChecklist.COMPLETADO);
            checklist.setCompletadoEn(ahora);
            checklists.saveAndFlush(checklist);
        }

        registrarHitoDeFase(cirugiaId, fase.getCodigoFase(), operador, ahora);
        eventos.registrar(cirugiaId, TipoEvento.FASE_CERRADA, operador, "fases_checklist_cirugia", faseId,
                Map.of("codigo", fase.getCodigoFase()));
        if (siguiente.isEmpty()) {
            eventos.registrar(cirugiaId, TipoEvento.CHECKLIST_COMPLETADO, operador, "checklists_cirugia",
                    checklist.getId(), Map.of("plantilla", checklist.getCodigoPlantilla()));
        }
        motor.evaluarAlConfirmar(cirugiaId);
        return vista(cirugiaId);
    }

    // ---------------------------------------------------------------------------------------------

    private void exigirAbierta(UUID cirugiaId) {
        EstadoCirugia estado = participacion.exigirCirugia(cirugiaId);
        if (CERRADAS.contains(estado)) {
            throw new ReglaNegocioException("La cirugía está " + estado + ": su checklist ya no admite registros");
        }
    }

    private Optional<UUID> operadorActual(UUID cirugiaId) {
        try {
            return Optional.of(participacion.exigirOperador(cirugiaId));
        } catch (ReglaNegocioException noEsOperador) {
            return Optional.empty();
        }
    }

    private ItemChecklistCirugia item(UUID cirugiaId, UUID itemId) {
        return items.findById(itemId)
                .filter(i -> i.getCirugiaId().equals(cirugiaId))
                .orElseThrow(() -> new RecursoNoEncontradoException("El ítem " + itemId + " no es de esta cirugía"));
    }

    /** Checklist EN_PROGRESO y la fase pedida es la actual: la de menor orden que no está cerrada. */
    private FaseChecklistCirugia exigirFaseActual(UUID cirugiaId, UUID faseId) {
        ChecklistCirugia checklist = checklists.findFirstByCirugiaIdOrderByCreadoEnDesc(cirugiaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La cirugía " + cirugiaId + " no tiene checklist"));
        if (checklist.getEstado() != EstadoChecklist.EN_PROGRESO) {
            throw new ReglaNegocioException(checklist.getEstado() == EstadoChecklist.PENDIENTE
                    ? "El checklist no se ha iniciado" : "El checklist ya está completado");
        }
        List<FaseChecklistCirugia> todas = fases.findAllByChecklistCirugiaIdOrderByOrden(checklist.getId());
        FaseChecklistCirugia actual = todas.stream()
                .filter(f -> f.getEstado() != EstadoFaseChecklist.COMPLETADA)
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException("Todas las fases del checklist están cerradas"));
        if (!actual.getId().equals(faseId)) {
            throw new ReglaNegocioException("Las fases se ejecutan en orden: la fase actual es «"
                    + actual.getNombreFase() + "»");
        }
        return actual;
    }

    /** config_validacion vive en el ítem de plantilla de origen (el snapshot no la copia). */
    private JsonNode configValidacion(ItemChecklistCirugia item) {
        if (item.getItemPlantillaOrigenId() == null) {
            return null;
        }
        return jdbc.sql("SELECT config_validacion::text FROM items_plantilla_checklist WHERE id = :id")
                .param("id", item.getItemPlantillaOrigenId())
                .query(String.class)
                .optional()
                .map(json::readTree)
                .orElse(null);
    }

    private void registrarHitoDeFase(UUID cirugiaId, String codigoFase, UUID operador, LocalDateTime ahora) {
        String tipo = HITO_POR_FASE.get(codigoFase);
        if (tipo == null) {
            return;
        }
        Optional<UUID> hito = jdbc.sql("""
                        INSERT INTO hitos_cirugia (cirugia_id, tipo_hito, ocurrido_en, registrado_por_asignacion_id, notas)
                        SELECT :cirugiaId, CAST(:tipo AS tipo_hito), :ahora, :operador, 'Registrado al cerrar la fase'
                        WHERE NOT EXISTS (SELECT 1 FROM hitos_cirugia
                                          WHERE cirugia_id = :cirugiaId AND tipo_hito = CAST(:tipo AS tipo_hito)
                                            AND anulado_en IS NULL)
                        RETURNING id
                        """)
                .param("cirugiaId", cirugiaId)
                .param("tipo", tipo)
                .param("ahora", ahora)
                .param("operador", operador)
                .query(UUID.class)
                .optional();
        hito.ifPresent(id -> eventos.registrar(cirugiaId, TipoEvento.HITO_REGISTRADO, operador, "hitos_cirugia", id,
                Map.of("tipo", tipo, "automatico", true)));
    }

    private ChecklistVista vista(UUID cirugiaId) {
        ChecklistVista vista = consultas.deCirugia(cirugiaId, cirugias.participantes(cirugiaId));
        if (vista == null) {
            throw new RecursoNoEncontradoException("La cirugía " + cirugiaId + " no tiene checklist");
        }
        return vista;
    }
}
