package com.hackaton.ulibre.tablero;

import static com.hackaton.ulibre.comun.Filas.enumeracion;
import static com.hackaton.ulibre.comun.Filas.fechaHora;
import static com.hackaton.ulibre.comun.Filas.uuid;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.hackaton.ulibre.alertas.Momento;
import com.hackaton.ulibre.alertas.MotorReglas;
import com.hackaton.ulibre.cirugias.CirugiasConsultas;
import com.hackaton.ulibre.cirugias.EstadoCirugia;
import com.hackaton.ulibre.cirugias.ParticipacionCirugia;
import com.hackaton.ulibre.cirugias.ParticipanteVista;
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
 * Hitos quirúrgicos. La base garantiza lo esencial: uno vigente por tipo (uq_hitos_vigentes), no se
 * borran ni se editan, solo se anulan una vez (fn_proteger_hito), TABLERO_INICIADO exige operador y
 * CIRUGIA_INICIADA exige instrumental completo. Los hitos no cambian el estado de la cirugía.
 */
@Service
public class HitosService {

    /** Margen para relojes de cliente ligeramente adelantados. */
    private static final Duration TOLERANCIA_FUTURO = Duration.ofMinutes(1);

    private final HitoCirugiaRepository hitos;
    private final JdbcClient jdbc;
    private final CirugiasConsultas cirugias;
    private final ParticipacionCirugia participacion;
    private final EventosCirugia eventos;
    private final MotorReglas motor;
    private final Clock clock;

    public HitosService(HitoCirugiaRepository hitos, JdbcClient jdbc, CirugiasConsultas cirugias,
            ParticipacionCirugia participacion, EventosCirugia eventos, MotorReglas motor, Clock clock) {
        this.hitos = hitos;
        this.jdbc = jdbc;
        this.cirugias = cirugias;
        this.participacion = participacion;
        this.eventos = eventos;
        this.motor = motor;
        this.clock = clock;
    }

    /** Cronológico; por defecto solo vigentes (anulado_en IS NULL). 2 consultas fijas. */
    @Transactional(readOnly = true)
    public List<HitoDetalle> listar(UUID cirugiaId, boolean incluirAnulados) {
        participacion.exigirCirugia(cirugiaId);
        Map<UUID, ParticipanteVista> participantes = cirugias.participantes(cirugiaId);
        return jdbc.sql("""
                        SELECT h.id, h.tipo_hito::text AS tipo_hito, h.ocurrido_en, h.registrado_por_asignacion_id,
                               h.notas, h.corrige_hito_id, h.anulado_en,
                               u.nombres || ' ' || u.apellidos AS anulado_por, h.motivo_anulacion, h.creado_en
                        FROM hitos_cirugia h
                                 LEFT JOIN usuarios u ON u.id = h.anulado_por_usuario_id
                        WHERE h.cirugia_id = :cirugiaId
                          AND (:incluirAnulados OR h.anulado_en IS NULL)
                        ORDER BY h.ocurrido_en, h.creado_en
                        """)
                .param("cirugiaId", cirugiaId)
                .param("incluirAnulados", incluirAnulados)
                .query((rs, n) -> {
                    UUID registradoPor = uuid(rs, "registrado_por_asignacion_id");
                    LocalDateTime anuladoEn = fechaHora(rs, "anulado_en");
                    return new HitoDetalle(
                            uuid(rs, "id"),
                            enumeracion(rs, "tipo_hito", TipoHito.class),
                            fechaHora(rs, "ocurrido_en"),
                            registradoPor == null ? null : participantes.get(registradoPor),
                            rs.getString("notas"),
                            uuid(rs, "corrige_hito_id"),
                            anuladoEn == null,
                            anuladoEn,
                            rs.getString("anulado_por"),
                            rs.getString("motivo_anulacion"),
                            fechaHora(rs, "creado_en"));
                })
                .list();
    }

    @Transactional
    public HitoDetalle registrar(UUID cirugiaId, NuevoHitoRequest datos) {
        EstadoCirugia estado = participacion.exigirCirugia(cirugiaId);
        if (estado == EstadoCirugia.CANCELADA || estado == EstadoCirugia.SUSPENDIDA) {
            throw new ReglaNegocioException("La cirugía está " + estado + ": no admite hitos");
        }
        UUID operador = participacion.exigirOperador(cirugiaId);
        LocalDateTime ahora = LocalDateTime.now(clock);
        LocalDateTime ocurridoEn = datos.ocurridoEn() == null ? ahora : datos.ocurridoEn();
        if (ocurridoEn.isAfter(ahora.plus(TOLERANCIA_FUTURO))) {
            throw new ReglaNegocioException("Un hito no puede registrarse con una hora futura");
        }
        if (datos.corrigeHitoId() != null) {
            HitoCirugia corregido = buscar(cirugiaId, datos.corrigeHitoId());
            if (corregido.getAnuladoEn() == null) {
                throw new ReglaNegocioException("Anule primero el hito que corrige");
            }
        }
        if (datos.tipoHito() == TipoHito.CIRUGIA_INICIADA) {
            // Antes de escribir: las alertas del inicio de cirugía quedan guardadas aunque la base lo rechace
            motor.evaluar(cirugiaId, Momento.inicioCirugia());
        }

        HitoCirugia hito = new HitoCirugia();
        hito.setCirugiaId(cirugiaId);
        hito.setTipoHito(datos.tipoHito());
        hito.setOcurridoEn(ocurridoEn);
        hito.setRegistradoPorAsignacionId(operador);
        hito.setNotas(Textos.limpiar(datos.notas()));
        hito.setCorrigeHitoId(datos.corrigeHitoId());
        hitos.saveAndFlush(hito);

        Map<String, Object> detalle = new HashMap<>();
        detalle.put("tipo", hito.getTipoHito().name());
        detalle.put("ocurridoEn", ocurridoEn.toString());
        if (hito.getCorrigeHitoId() != null) {
            detalle.put("corrigeHitoId", hito.getCorrigeHitoId());
        }
        eventos.registrar(cirugiaId, TipoEvento.HITO_REGISTRADO, operador, "hitos_cirugia", hito.getId(), detalle);
        motor.evaluarAlConfirmar(cirugiaId);
        return detalle(cirugiaId, hito.getId());
    }

    @Transactional
    public HitoDetalle anular(UUID cirugiaId, UUID hitoId, AnularHitoRequest datos) {
        participacion.exigirCirugia(cirugiaId);
        UUID operador = participacion.exigirOperador(cirugiaId);
        HitoCirugia hito = buscar(cirugiaId, hitoId);
        hito.setAnuladoEn(LocalDateTime.now(clock));
        hito.setAnuladoPorUsuarioId(UsuarioActual.id());
        hito.setMotivoAnulacion(Textos.limpiar(datos.motivo()));
        hitos.saveAndFlush(hito);   // anular dos veces lo rechaza fn_proteger_hito
        eventos.registrar(cirugiaId, TipoEvento.HITO_ANULADO, operador, "hitos_cirugia", hitoId,
                Map.of("tipo", hito.getTipoHito().name()));
        motor.evaluarAlConfirmar(cirugiaId);
        return detalle(cirugiaId, hitoId);
    }

    private HitoCirugia buscar(UUID cirugiaId, UUID hitoId) {
        return hitos.findById(hitoId)
                .filter(h -> h.getCirugiaId().equals(cirugiaId))
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El hito " + hitoId + " no pertenece a la cirugía " + cirugiaId));
    }

    private HitoDetalle detalle(UUID cirugiaId, UUID hitoId) {
        return listar(cirugiaId, true).stream().filter(h -> h.id().equals(hitoId)).findFirst().orElseThrow();
    }
}
