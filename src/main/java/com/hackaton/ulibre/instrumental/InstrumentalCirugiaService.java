package com.hackaton.ulibre.instrumental;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

/**
 * Instrumental de una cirugía: el snapshot lo crea fn_preparar_instrumental y aquí solo se registra
 * cuánto se preparó y quién (la asignación de quien prepara). Completitud y faltantes se leen con
 * InstrumentalConsultas, con el mismo criterio que fn_exigir_instrumental_completo.
 */
@Service
public class InstrumentalCirugiaService {

    /** Cirugías sobre las que ya no se prepara instrumental. */
    private static final Set<EstadoCirugia> CERRADAS =
            EnumSet.of(EstadoCirugia.CANCELADA, EstadoCirugia.SUSPENDIDA, EstadoCirugia.COMPLETADA);

    private final JdbcClient jdbc;
    private final SetInstrumentalCirugiaRepository sets;
    private final InstrumentoCirugiaRepository instrumentos;
    private final InstrumentalConsultas consultas;
    private final CirugiasConsultas cirugias;
    private final ParticipacionCirugia participacion;
    private final EventosCirugia eventos;
    private final MotorReglas motor;
    private final Clock clock;

    public InstrumentalCirugiaService(JdbcClient jdbc, SetInstrumentalCirugiaRepository sets,
            InstrumentoCirugiaRepository instrumentos, InstrumentalConsultas consultas, CirugiasConsultas cirugias,
            ParticipacionCirugia participacion, EventosCirugia eventos, MotorReglas motor, Clock clock) {
        this.jdbc = jdbc;
        this.sets = sets;
        this.instrumentos = instrumentos;
        this.consultas = consultas;
        this.cirugias = cirugias;
        this.participacion = participacion;
        this.eventos = eventos;
        this.motor = motor;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InstrumentalVista obtener(UUID cirugiaId) {
        participacion.exigirCirugia(cirugiaId);
        return vista(cirugiaId);
    }

    /** Copia el instrumental predeterminado del procedimiento (fn_preparar_instrumental). */
    @Transactional
    public InstrumentalVista preparar(UUID cirugiaId) {
        exigirAbierta(cirugiaId);
        int copiados = jdbc.sql("SELECT fn_preparar_instrumental(:cirugiaId)")
                .param("cirugiaId", cirugiaId)
                .query(Integer.class)
                .single();
        eventos.registrar(cirugiaId, TipoEvento.INSTRUMENTAL_COPIADO, participacion.asignacionVigente(cirugiaId)
                .orElse(null), "cirugias", cirugiaId, Map.of("copiados", copiados));
        motor.evaluarAlConfirmar(cirugiaId);
        return vista(cirugiaId);
    }

    @Transactional
    public InstrumentalVista prepararSet(UUID cirugiaId, UUID setCirugiaId, PreparacionInstrumentalRequest datos) {
        exigirAbierta(cirugiaId);
        SetInstrumentalCirugia set = sets.findById(setCirugiaId)
                .filter(s -> s.getCirugiaId().equals(cirugiaId))
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El set " + setCirugiaId + " no pertenece a la cirugía " + cirugiaId));
        UUID preparadoPor = participacion.exigirAsignado(cirugiaId);
        int cantidad = datos.cantidadPreparada();
        set.setCantidadPreparada(cantidad);
        set.setPreparadoPorAsignacionId(cantidad == 0 ? null : preparadoPor);
        set.setPreparadoEn(cantidad == 0 ? null : LocalDateTime.now(clock));
        set.setNotas(Textos.limpiar(datos.notas()));
        sets.saveAndFlush(set);
        eventos.registrar(cirugiaId, TipoEvento.INSTRUMENTAL_PREPARADO, preparadoPor, "sets_instrumentales_cirugia",
                set.getId(), Map.of("tipo", "SET", "codigo", set.getCodigoSet(), "cantidadPreparada", cantidad,
                        "cantidadRequerida", set.getCantidadRequerida()));
        motor.evaluarAlConfirmar(cirugiaId);
        return vista(cirugiaId);
    }

    @Transactional
    public InstrumentalVista prepararInstrumento(UUID cirugiaId, UUID instrumentoCirugiaId,
            PreparacionInstrumentalRequest datos) {
        exigirAbierta(cirugiaId);
        InstrumentoCirugia instrumento = instrumentos.findById(instrumentoCirugiaId)
                .filter(i -> i.getCirugiaId().equals(cirugiaId))
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El instrumento " + instrumentoCirugiaId + " no pertenece a la cirugía " + cirugiaId));
        UUID preparadoPor = participacion.exigirAsignado(cirugiaId);
        int cantidad = datos.cantidadPreparada();
        instrumento.setCantidadPreparada(cantidad);
        instrumento.setPreparadoPorAsignacionId(cantidad == 0 ? null : preparadoPor);
        instrumento.setPreparadoEn(cantidad == 0 ? null : LocalDateTime.now(clock));
        instrumento.setNotas(Textos.limpiar(datos.notas()));
        instrumentos.saveAndFlush(instrumento);
        eventos.registrar(cirugiaId, TipoEvento.INSTRUMENTAL_PREPARADO, preparadoPor, "instrumentos_cirugia",
                instrumento.getId(), Map.of("tipo", "INSTRUMENTO", "codigo", instrumento.getCodigoInstrumento(),
                        "cantidadPreparada", cantidad, "cantidadRequerida", instrumento.getCantidadRequerida()));
        motor.evaluarAlConfirmar(cirugiaId);
        return vista(cirugiaId);
    }

    private void exigirAbierta(UUID cirugiaId) {
        EstadoCirugia estado = participacion.exigirCirugia(cirugiaId);
        if (CERRADAS.contains(estado)) {
            throw new ReglaNegocioException("La cirugía está " + estado + ": su instrumental ya no se prepara");
        }
    }

    private InstrumentalVista vista(UUID cirugiaId) {
        return consultas.deCirugia(cirugiaId, cirugias.participantes(cirugiaId));
    }
}
