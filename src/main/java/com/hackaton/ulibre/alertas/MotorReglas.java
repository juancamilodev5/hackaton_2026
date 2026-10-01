package com.hackaton.ulibre.alertas;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.hackaton.ulibre.checklist.EstadoFaseChecklist;
import com.hackaton.ulibre.cirugias.EstadoCirugia;
import com.hackaton.ulibre.comun.UsuarioActual;
import com.hackaton.ulibre.eventos.EventosCirugia;
import com.hackaton.ulibre.eventos.TipoEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Motor de reglas de seguridad. reglas_seguridad solo configura (activa, severidad, bloqueante);
 * la lógica de cada regla vive aquí, identificada por su código (semilla V2). No se inventan reglas:
 * hay exactamente una función por cada código sembrado.
 *
 * <p>Evaluar una regla activa da uno de tres resultados:
 * <ul>
 *   <li><b>hallazgo</b>: si no hay alerta vigente de la regla se crea una (ABIERTA, con la severidad y
 *       el bloqueo configurados); una sola vigente por regla y cirugía (ux_alerta_vigente_por_regla);</li>
 *   <li><b>ok</b>: si había una alerta vigente, se resuelve sola (la condición desapareció);</li>
 *   <li><b>no aplica</b> (el flujo aún no llegó al punto en que la regla se exige): no se toca nada.</li>
 * </ul>
 *
 * <p>Se evalúa en una transacción propia (REQUIRES_NEW) para que las alertas queden guardadas aunque
 * la operación que la disparó falle después: cerrar una fase la rechaza el trigger
 * fn_validar_cierre_fase si hay alertas bloqueantes abiertas, y esas alertas deben seguir ahí.
 */
@Service
public class MotorReglas {

    private static final Logger log = LoggerFactory.getLogger(MotorReglas.class);

    /** Estados en los que la cirugía ya empezó (la incisión ya ocurrió). */
    private static final Set<EstadoCirugia> CIRUGIA_EN_CURSO = EnumSet.of(EstadoCirugia.EN_CIRUGIA,
            EstadoCirugia.CIERRE, EstadoCirugia.RECUPERACION, EstadoCirugia.COMPLETADA);

    private final JdbcClient jdbc;
    private final EventosCirugia eventos;
    private final Clock clock;
    private final TransactionTemplate nuevaTransaccion;
    private final Map<String, Function<Evaluacion, Resultado>> reglas = Map.of(
            "DATOS_OBLIGATORIOS_INCOMPLETOS", MotorReglas::datosObligatorios,
            "SITIO_QUIRURGICO_NO_CONFIRMADO", MotorReglas::sitioQuirurgico,
            "ALERGIA_NO_CONFIRMADA", MotorReglas::alergia,
            "ANTIBIOTICO_PENDIENTE", MotorReglas::antibiotico,
            "EQUIPO_QUIRURGICO_INCOMPLETO", MotorReglas::equipo,
            "OPERADOR_TABLERO_NO_ASIGNADO", MotorReglas::operador,
            "INSTRUMENTAL_REQUERIDO_INCOMPLETO", MotorReglas::instrumental,
            "RECUENTO_INCONSISTENTE", MotorReglas::recuentos,
            "FASE_OBLIGATORIA_INCOMPLETA", MotorReglas::faseIncompleta);

    public MotorReglas(JdbcClient jdbc, EventosCirugia eventos, Clock clock, PlatformTransactionManager tx) {
        this.jdbc = jdbc;
        this.eventos = eventos;
        this.clock = clock;
        this.nuevaTransaccion = new TransactionTemplate(tx);
        this.nuevaTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Evalúa ya, en su propia transacción. Llamarlo ANTES de escribir nada en la transacción actual:
     * la nueva no ve lo que la actual aún no confirmó.
     */
    public ResultadoEvaluacion evaluar(UUID cirugiaId, Momento momento) {
        return nuevaTransaccion.execute(estado -> evaluarEnTransaccion(cirugiaId, momento));
    }

    /**
     * Evalúa cuando la transacción actual confirme (para reglas que dependen de lo que se acaba de
     * registrar). Un fallo del motor no deshace la acción ya confirmada: se registra en el log.
     */
    public void evaluarAlConfirmar(UUID cirugiaId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evaluar(cirugiaId, Momento.evaluacion());
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    evaluar(cirugiaId, Momento.evaluacion());
                } catch (RuntimeException ex) {
                    log.error("No se pudieron reevaluar las reglas de la cirugía {}", cirugiaId, ex);
                }
            }
        });
    }

    private ResultadoEvaluacion evaluarEnTransaccion(UUID cirugiaId, Momento momento) {
        // Serializa evaluaciones concurrentes de la misma cirugía (dos registros simultáneos en el tablero)
        jdbc.sql("SELECT id FROM cirugias WHERE id = :id FOR UPDATE").param("id", cirugiaId).query(UUID.class).single();
        Evaluacion evaluacion = new Evaluacion(ContextoReglas.cargar(jdbc, cirugiaId), momento);

        List<Regla> activas = jdbc.sql("""
                        SELECT r.id, r.codigo, r.nombre, r.severidad::text AS severidad, r.bloqueante,
                               a.id AS alerta_vigente_id
                        FROM reglas_seguridad r
                                 LEFT JOIN alertas a ON a.regla_seguridad_id = r.id AND a.cirugia_id = :cirugiaId
                                     AND a.estado IN ('ABIERTA', 'RECONOCIDA')
                        WHERE r.activo
                        ORDER BY r.codigo
                        """)
                .param("cirugiaId", cirugiaId)
                .query((rs, n) -> new Regla(
                        rs.getObject("id", UUID.class),
                        rs.getString("codigo"),
                        rs.getString("nombre"),
                        SeveridadAlerta.valueOf(rs.getString("severidad")),
                        rs.getBoolean("bloqueante"),
                        rs.getObject("alerta_vigente_id", UUID.class)))
                .list();

        int generadas = 0;
        int resueltas = 0;
        for (Regla regla : activas) {
            Function<Evaluacion, Resultado> logica = reglas.get(regla.codigo());
            if (logica == null) {
                continue;   // regla configurada sin implementación en esta versión del backend
            }
            Resultado resultado = logica.apply(evaluacion);
            if (resultado instanceof Resultado.Hallazgo hallazgo && regla.alertaVigenteId() == null) {
                generar(cirugiaId, regla, hallazgo);
                generadas++;
            } else if (resultado instanceof Resultado.Ok && regla.alertaVigenteId() != null) {
                resolver(cirugiaId, regla);
                resueltas++;
            }
        }
        return new ResultadoEvaluacion(generadas, resueltas);
    }

    private void generar(UUID cirugiaId, Regla regla, Resultado.Hallazgo hallazgo) {
        UUID alertaId = jdbc.sql("""
                        INSERT INTO alertas (cirugia_id, regla_seguridad_id, tipo_origen, origen_id, severidad,
                                             bloqueante, estado, titulo, mensaje, disparada_en)
                        VALUES (:cirugiaId, :reglaId, 'REGLA', :origenId, CAST(:severidad AS severidad_alerta),
                                :bloqueante, 'ABIERTA', :titulo, :mensaje, :ahora)
                        RETURNING id
                        """)
                .param("cirugiaId", cirugiaId)
                .param("reglaId", regla.id())
                .param("origenId", hallazgo.origenId())
                .param("severidad", regla.severidad().name())
                .param("bloqueante", regla.bloqueante())
                .param("titulo", regla.nombre())
                .param("mensaje", hallazgo.mensaje())
                .param("ahora", LocalDateTime.now(clock))
                .query(UUID.class)
                .single();
        eventos.registrar(cirugiaId, TipoEvento.ALERTA_GENERADA, null, "alertas", alertaId,
                Map.of("regla", regla.codigo(), "severidad", regla.severidad().name(), "bloqueante", regla.bloqueante()));
    }

    private void resolver(UUID cirugiaId, Regla regla) {
        jdbc.sql("""
                        UPDATE alertas
                        SET estado = 'RESUELTA', resuelta_por_usuario_id = :usuarioId, resuelta_en = :ahora,
                            notas_resolucion = 'Resuelta automáticamente al reevaluar: la condición ya no se cumple'
                        WHERE id = :alertaId
                        """)
                .param("usuarioId", UsuarioActual.id())
                .param("ahora", LocalDateTime.now(clock))
                .param("alertaId", regla.alertaVigenteId())
                .update();
        eventos.registrar(cirugiaId, TipoEvento.ALERTA_RESUELTA, null, "alertas", regla.alertaVigenteId(),
                Map.of("regla", regla.codigo(), "automatica", true));
    }

    /** Si la condición de una regla sigue presente (para no dejar resolver a mano una alerta vigente). */
    public boolean condicionPresente(UUID cirugiaId, String codigoRegla) {
        Function<Evaluacion, Resultado> logica = reglas.get(codigoRegla);
        if (logica == null) {
            return false;
        }
        return logica.apply(new Evaluacion(ContextoReglas.cargar(jdbc, cirugiaId), Momento.evaluacion()))
                instanceof Resultado.Hallazgo;
    }

    // ---------------------------------------------------------------------------------------------
    // Reglas (una por código sembrado). Los códigos de ítems son los de la plantilla
    // SEGURIDAD_QUIRURGICA_ESTANDAR; si la plantilla usada no los tiene, la regla no aplica.
    // ---------------------------------------------------------------------------------------------

    /** Peso, talla y validación del preoperatorio, y sitio quirúrgico de la solicitud. Desde que hay checklist. */
    private static Resultado datosObligatorios(Evaluacion e) {
        if (e.ctx().fases().isEmpty()) {
            return Resultado.NO_APLICA;
        }
        List<String> faltan = new ArrayList<>();
        ContextoReglas.Preoperatorio preop = e.ctx().preoperatorio();
        if (preop == null) {
            faltan.add("datos preoperatorios");
        } else {
            if (preop.pesoKg() == null) {
                faltan.add("peso");
            }
            if (preop.tallaCm() == null) {
                faltan.add("talla");
            }
            if (preop.validadoEn() == null) {
                faltan.add("validación del preoperatorio");
            }
        }
        if (!e.ctx().sitioQuirurgicoRegistrado()) {
            faltan.add("sitio quirúrgico en la solicitud");
        }
        return faltan.isEmpty() ? Resultado.OK : Resultado.hallazgo("Faltan: " + String.join(", ", faltan));
    }

    /** Sitio confirmado antes de la incisión. */
    private static Resultado sitioQuirurgico(Evaluacion e) {
        if (!e.antesDeIncision()) {
            return Resultado.NO_APLICA;
        }
        return e.ctx().item("SITIO_CONFIRMADO").or(() -> e.ctx().item("SITIO_QUIRURGICO"))
                .map(i -> i.satisfecho() ? Resultado.OK
                        : Resultado.hallazgo("El ítem «" + i.etiqueta() + "» no está confirmado", i.id()))
                .orElse(Resultado.NO_APLICA);
    }

    /** Alergias registradas confirmadas antes de la anestesia (cierre de PREANESTESIA). */
    private static Resultado alergia(Evaluacion e) {
        if (!e.alcanzada("PREANESTESIA")) {
            return Resultado.NO_APLICA;
        }
        if (e.ctx().alergiasPaciente() == 0) {
            return Resultado.OK;
        }
        return e.ctx().item("ALERGIAS")
                .map(i -> i.satisfecho() ? Resultado.OK
                        : Resultado.hallazgo("El paciente tiene " + e.ctx().alergiasPaciente()
                                + " alergia(s) registrada(s) y el ítem «" + i.etiqueta() + "» no está confirmado", i.id()))
                .orElse(Resultado.NO_APLICA);
    }

    /** Antibiótico profiláctico registrado antes de la incisión. */
    private static Resultado antibiotico(Evaluacion e) {
        if (!e.antesDeIncision()) {
            return Resultado.NO_APLICA;
        }
        return e.ctx().item("ANTIBIOTICO_PROFILACTICO")
                .map(i -> i.satisfecho() ? Resultado.OK
                        : Resultado.hallazgo("El ítem «" + i.etiqueta() + "» no está confirmado", i.id()))
                .orElse(Resultado.NO_APLICA);
    }

    /** Requerimientos obligatorios sin cubrir (regla 2: Anestesiólogo x2 con uno solo no es equipo completo). */
    private static Resultado equipo(Evaluacion e) {
        List<String> faltan = e.ctx().requerimientosSinCubrir();
        return faltan.isEmpty() ? Resultado.OK : Resultado.hallazgo("Sin cubrir: " + String.join(", ", faltan));
    }

    private static Resultado operador(Evaluacion e) {
        return e.ctx().hayOperador() ? Resultado.OK
                : Resultado.hallazgo("Ninguna asignación vigente está designada como operador del tablero");
    }

    /** Mismo criterio que fn_exigir_instrumental_completo. Exigible desde la preanestesia (ítem INSTRUMENTAL_PREPARADO). */
    private static Resultado instrumental(Evaluacion e) {
        if (!e.alcanzada("PREANESTESIA")) {
            return Resultado.NO_APLICA;
        }
        if (!e.ctx().instrumentalPreparado()) {
            return e.ctx().procedimientoTieneInstrumental()
                    ? Resultado.hallazgo("El instrumental predeterminado del procedimiento no se ha preparado")
                    : Resultado.OK;
        }
        List<String> faltan = e.ctx().instrumentalFaltante();
        return faltan.isEmpty() ? Resultado.OK : Resultado.hallazgo("Falta: " + String.join(", ", faltan));
    }

    /** Estado DISCREPANCIA según la vista v_recuentos_cirugia (no se recalcula aquí). */
    private static Resultado recuentos(Evaluacion e) {
        List<String> discrepancias = e.ctx().recuentosConDiscrepancia();
        return discrepancias.isEmpty() ? Resultado.OK
                : Resultado.hallazgo("No cuadra: " + String.join("; ", discrepancias));
    }

    /**
     * Fases cerradas o que se intentan cerrar: los ítems obligatorios deben estar respondidos (y
     * confirmados si tienen rol responsable) y los bloqueantes, satisfechos (COMPLETADO o NO_APLICA).
     */
    private static Resultado faseIncompleta(Evaluacion e) {
        List<String> problemas = new ArrayList<>();
        UUID origen = null;
        for (ContextoReglas.Fase fase : e.ctx().fases()) {
            boolean exigible = fase.estado() == EstadoFaseChecklist.COMPLETADA
                    || (e.momento().tipo() == Momento.Tipo.CIERRE_FASE && fase.codigo().equals(e.momento().codigoFase()));
            if (!exigible) {
                continue;
            }
            List<String> pendientes = fase.items().stream()
                    .filter(i -> (i.obligatorio() && !i.completo()) || (i.bloqueante() && !i.satisfecho()))
                    .map(ContextoReglas.Item::etiqueta)
                    .toList();
            if (!pendientes.isEmpty()) {
                problemas.add(fase.nombre() + ": " + String.join(", ", pendientes));
                origen = origen == null ? fase.id() : origen;
            }
        }
        if (problemas.isEmpty()) {
            // Sin fases exigibles todavía no hay nada que afirmar: la alerta (si existe) se conserva
            boolean algunaExigible = e.ctx().fases().stream().anyMatch(f -> f.estado() == EstadoFaseChecklist.COMPLETADA)
                    || e.momento().tipo() == Momento.Tipo.CIERRE_FASE;
            return algunaExigible ? Resultado.OK : Resultado.NO_APLICA;
        }
        return Resultado.hallazgo("Ítems pendientes o no satisfechos — " + String.join(" | ", problemas), origen);
    }

    // ---------------------------------------------------------------------------------------------

    private record Regla(UUID id, String codigo, String nombre, SeveridadAlerta severidad, boolean bloqueante,
                         UUID alertaVigenteId) {
    }

    /** Contexto + momento, con las "puertas" del flujo. */
    private record Evaluacion(ContextoReglas ctx, Momento momento) {

        boolean iniciandoOIniciada() {
            return momento.tipo() == Momento.Tipo.INICIO_CIRUGIA || ctx.cirugiaIniciada()
                    || CIRUGIA_EN_CURSO.contains(ctx.estado());
        }

        /** La fase (o una posterior) está cerrada o se está cerrando, o la cirugía ya inicia. */
        boolean alcanzada(String codigoFase) {
            if (iniciandoOIniciada()) {
                return true;
            }
            Optional<ContextoReglas.Fase> fase = ctx.fase(codigoFase);
            if (fase.isEmpty()) {
                return false;
            }
            int orden = fase.get().orden();
            return ctx.fases().stream().anyMatch(f -> f.orden() >= orden
                    && (f.estado() == EstadoFaseChecklist.COMPLETADA
                    || (momento.tipo() == Momento.Tipo.CIERRE_FASE && f.codigo().equals(momento.codigoFase()))));
        }

        boolean antesDeIncision() {
            return alcanzada("PREINCISION");
        }
    }

    sealed interface Resultado {
        Resultado OK = new Ok();
        Resultado NO_APLICA = new NoAplica();

        record Ok() implements Resultado {
        }

        record NoAplica() implements Resultado {
        }

        record Hallazgo(String mensaje, UUID origenId) implements Resultado {
        }

        static Resultado hallazgo(String mensaje) {
            return new Hallazgo(mensaje, null);
        }

        static Resultado hallazgo(String mensaje, UUID origenId) {
            return new Hallazgo(mensaje, origenId);
        }
    }

    public record ResultadoEvaluacion(int alertasGeneradas, int alertasResueltas) {
    }
}
