package com.hackaton.ulibre.alertas;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.ParticipacionCirugia;
import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.ReglaNegocioException;
import com.hackaton.ulibre.comun.Textos;
import com.hackaton.ulibre.comun.UsuarioActual;
import com.hackaton.ulibre.eventos.EventosCirugia;
import com.hackaton.ulibre.eventos.TipoEvento;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de alertas. Que el cierre y la excepción vayan completos (quién + cuándo + motivo) y que
 * solo se exceptúen alertas bloqueantes lo exigen los CHECK de la tabla; aquí solo se controlan las
 * transiciones y que una alerta no desaparezca mientras su causa siga presente.
 */
@Service
public class AlertasService {

    private final AlertaRepository alertas;
    private final AlertasConsultas consultas;
    private final MotorReglas motor;
    private final EventosCirugia eventos;
    private final ParticipacionCirugia participacion;
    private final JdbcClient jdbc;
    private final Clock clock;

    public AlertasService(AlertaRepository alertas, AlertasConsultas consultas, MotorReglas motor,
            EventosCirugia eventos, ParticipacionCirugia participacion, JdbcClient jdbc, Clock clock) {
        this.alertas = alertas;
        this.consultas = consultas;
        this.motor = motor;
        this.eventos = eventos;
        this.participacion = participacion;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AlertaVista> listar(UUID cirugiaId) {
        participacion.exigirCirugia(cirugiaId);
        return consultas.deCirugia(cirugiaId);
    }

    /** La evaluación abre su propia transacción; la lectura posterior ya ve sus alertas. */
    public EvaluacionReglasResponse evaluar(UUID cirugiaId) {
        participacion.exigirCirugia(cirugiaId);
        MotorReglas.ResultadoEvaluacion resultado = motor.evaluar(cirugiaId, Momento.evaluacion());
        return new EvaluacionReglasResponse(resultado.alertasGeneradas(), resultado.alertasResueltas(),
                consultas.deCirugia(cirugiaId));
    }

    @Transactional
    public AlertaVista reconocer(UUID cirugiaId, UUID alertaId) {
        Alerta alerta = buscar(cirugiaId, alertaId);
        if (alerta.getEstado() != EstadoAlerta.ABIERTA) {
            throw new ReglaNegocioException("Solo se reconoce una alerta ABIERTA (estado actual: "
                    + alerta.getEstado() + ")");
        }
        alerta.setEstado(EstadoAlerta.RECONOCIDA);
        alerta.setReconocidaPorUsuarioId(UsuarioActual.id());
        alerta.setReconocidaEn(LocalDateTime.now(clock));
        alertas.saveAndFlush(alerta);
        registrar(alerta, TipoEvento.ALERTA_RECONOCIDA);
        return vista(cirugiaId, alertaId);
    }

    /** Una alerta de regla no se resuelve a mano mientras su condición siga presente. */
    @Transactional
    public AlertaVista resolver(UUID cirugiaId, UUID alertaId, NotasAlertaRequest datos) {
        Alerta alerta = buscarVigente(cirugiaId, alertaId);
        String regla = codigoRegla(alerta);
        if (regla != null && motor.condicionPresente(cirugiaId, regla)) {
            throw new ReglaNegocioException("La condición que generó la alerta sigue presente: "
                    + "corríjala o autorice una excepción");
        }
        cerrar(alerta, EstadoAlerta.RESUELTA, datos.notas());
        registrar(alerta, TipoEvento.ALERTA_RESUELTA);
        return vista(cirugiaId, alertaId);
    }

    /** Descartar es para avisos que no aplican; una bloqueante nunca "desaparece" por un botón. */
    @Transactional
    public AlertaVista descartar(UUID cirugiaId, UUID alertaId, NotasAlertaRequest datos) {
        Alerta alerta = buscarVigente(cirugiaId, alertaId);
        if (alerta.isBloqueante()) {
            throw new ReglaNegocioException("Una alerta bloqueante no se descarta: resuélvala o autorice una excepción");
        }
        cerrar(alerta, EstadoAlerta.DESCARTADA, datos.notas());
        registrar(alerta, TipoEvento.ALERTA_DESCARTADA);
        return vista(cirugiaId, alertaId);
    }

    /**
     * Regla 16: la alerta sigue vigente y visible, pero los triggers (cierre de fase, inicio de
     * cirugía) dejan de contarla. Que sea bloqueante lo exige ck_alerta_excepcion_bloqueante.
     */
    @Transactional
    public AlertaVista autorizarExcepcion(UUID cirugiaId, UUID alertaId, ExcepcionAlertaRequest datos) {
        Alerta alerta = buscarVigente(cirugiaId, alertaId);
        if (alerta.getExcepcionAutorizadaPorUsuarioId() != null) {
            throw new ReglaNegocioException("La alerta ya tiene una excepción autorizada");
        }
        alerta.setExcepcionAutorizadaPorUsuarioId(UsuarioActual.id());
        alerta.setExcepcionAutorizadaEn(LocalDateTime.now(clock));
        alerta.setMotivoExcepcion(Textos.limpiar(datos.motivo()));
        alertas.saveAndFlush(alerta);
        registrar(alerta, TipoEvento.EXCEPCION_AUTORIZADA);
        return vista(cirugiaId, alertaId);
    }

    private void cerrar(Alerta alerta, EstadoAlerta estado, String notas) {
        alerta.setEstado(estado);
        alerta.setResueltaPorUsuarioId(UsuarioActual.id());
        alerta.setResueltaEn(LocalDateTime.now(clock));
        alerta.setNotasResolucion(Textos.limpiar(notas));
        alertas.saveAndFlush(alerta);
    }

    private void registrar(Alerta alerta, TipoEvento tipo) {
        Map<String, Object> datos = new LinkedHashMap<>();
        String regla = codigoRegla(alerta);
        if (regla != null) {
            datos.put("regla", regla);
        }
        datos.put("estado", alerta.getEstado().name());
        datos.put("bloqueante", alerta.isBloqueante());
        eventos.registrar(alerta.getCirugiaId(), tipo,
                participacion.asignacionVigente(alerta.getCirugiaId()).orElse(null), "alertas", alerta.getId(), datos);
    }

    private String codigoRegla(Alerta alerta) {
        if (alerta.getReglaSeguridadId() == null) {
            return null;
        }
        return jdbc.sql("SELECT codigo FROM reglas_seguridad WHERE id = :id")
                .param("id", alerta.getReglaSeguridadId())
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private Alerta buscarVigente(UUID cirugiaId, UUID alertaId) {
        Alerta alerta = buscar(cirugiaId, alertaId);
        if (!alerta.getEstado().vigente()) {
            throw new ReglaNegocioException("La alerta ya está " + alerta.getEstado());
        }
        return alerta;
    }

    private Alerta buscar(UUID cirugiaId, UUID alertaId) {
        participacion.exigirCirugia(cirugiaId);
        return alertas.findByIdAndCirugiaId(alertaId, cirugiaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Alerta no encontrada: " + alertaId));
    }

    private AlertaVista vista(UUID cirugiaId, UUID alertaId) {
        return consultas.deCirugia(cirugiaId).stream()
                .filter(a -> a.id().equals(alertaId))
                .findFirst()
                .orElseThrow();
    }
}
