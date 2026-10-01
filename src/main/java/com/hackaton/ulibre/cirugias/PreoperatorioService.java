package com.hackaton.ulibre.cirugias;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.hackaton.ulibre.alertas.MotorReglas;
import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.Textos;
import com.hackaton.ulibre.comun.UsuarioActual;
import com.hackaton.ulibre.eventos.EventosCirugia;
import com.hackaton.ulibre.eventos.TipoEvento;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Datos preoperatorios (snapshot). Las alergias se copian del paciente al crearlos y solo se
 * vuelven a copiar a pedido, para que el registro conserve lo que se usó en la cirugía.
 */
@Service
public class PreoperatorioService {

    private final DatosPreoperatoriosCirugiaRepository preoperatorios;
    private final CirugiaRepository cirugias;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final EventosCirugia eventos;
    private final MotorReglas motor;
    private final ParticipacionCirugia participacion;

    public PreoperatorioService(DatosPreoperatoriosCirugiaRepository preoperatorios, CirugiaRepository cirugias,
            JdbcClient jdbc, Clock clock, EventosCirugia eventos, MotorReglas motor,
            ParticipacionCirugia participacion) {
        this.preoperatorios = preoperatorios;
        this.cirugias = cirugias;
        this.jdbc = jdbc;
        this.clock = clock;
        this.eventos = eventos;
        this.motor = motor;
        this.participacion = participacion;
    }

    @Transactional(readOnly = true)
    public PreoperatorioResponse obtener(UUID cirugiaId) {
        return respuesta(buscar(cirugiaId));
    }

    @Transactional
    public PreoperatorioResponse guardar(UUID cirugiaId, PreoperatorioRequest datos) {
        DatosPreoperatoriosCirugia preop = preoperatorios.findByCirugiaId(cirugiaId).orElseGet(() -> {
            exigirCirugia(cirugiaId);
            DatosPreoperatoriosCirugia nuevo = new DatosPreoperatoriosCirugia();
            nuevo.setCirugiaId(cirugiaId);
            nuevo.setCopiaAlergias(alergiasActivas(cirugiaId));
            return nuevo;
        });
        preop.setPesoKg(datos.pesoKg());
        preop.setTallaCm(datos.tallaCm());
        preop.setGlucometriaMgDl(datos.glucometriaMgDl());
        preop.setRequiereReservaSangre(datos.requiereReservaSangre());
        preop.setEstadoReservaSangre(datos.estadoReservaSangre() != null ? datos.estadoReservaSangre()
                : datos.requiereReservaSangre() ? EstadoReservaSangre.PENDIENTE : EstadoReservaSangre.NO_REQUERIDA);
        preop.setInformacionClinicaRelevante(Textos.limpiar(datos.informacionClinicaRelevante()));
        preop.setDatosAdicionales(datos.datosAdicionales() == null || datos.datosAdicionales().isNull() ? null
                : datos.datosAdicionales().toString());
        invalidarValidacion(preop);
        preoperatorios.saveAndFlush(preop);
        registrar(cirugiaId, TipoEvento.PREOPERATORIO_REGISTRADO, preop, Map.of());
        return respuesta(preop);
    }

    @Transactional
    public PreoperatorioResponse actualizarAlergias(UUID cirugiaId) {
        DatosPreoperatoriosCirugia preop = buscar(cirugiaId);
        preop.setCopiaAlergias(alergiasActivas(cirugiaId));
        invalidarValidacion(preop);
        preoperatorios.saveAndFlush(preop);
        registrar(cirugiaId, TipoEvento.PREOPERATORIO_REGISTRADO, preop, Map.of("alergiasActualizadas", true));
        return respuesta(preop);
    }

    @Transactional
    public PreoperatorioResponse validar(UUID cirugiaId) {
        DatosPreoperatoriosCirugia preop = buscar(cirugiaId);
        preop.setValidadoPorUsuarioId(UsuarioActual.id());
        preop.setValidadoEn(LocalDateTime.now(clock));
        preoperatorios.saveAndFlush(preop);
        registrar(cirugiaId, TipoEvento.PREOPERATORIO_VALIDADO, preop, Map.of());
        return respuesta(preop);
    }

    private void registrar(UUID cirugiaId, TipoEvento tipo, DatosPreoperatoriosCirugia preop, Map<String, ?> datos) {
        eventos.registrar(cirugiaId, tipo, participacion.asignacionVigente(cirugiaId).orElse(null),
                "datos_preoperatorios_cirugia", preop.getId(), datos);
        motor.evaluarAlConfirmar(cirugiaId);
    }

    /** Cualquier cambio obliga a validar de nuevo. */
    private static void invalidarValidacion(DatosPreoperatoriosCirugia preop) {
        preop.setValidadoPorUsuarioId(null);
        preop.setValidadoEn(null);
    }

    /** Mismo formato que los datos demo: arreglo de {sustancia, reaccion, severidad}. */
    private String alergiasActivas(UUID cirugiaId) {
        return jdbc.sql("""
                        SELECT coalesce(jsonb_agg(jsonb_build_object('sustancia', a.sustancia,
                                                                     'reaccion', a.reaccion,
                                                                     'severidad', a.severidad)
                                                  ORDER BY a.sustancia), '[]'::jsonb)::text AS alergias
                        FROM cirugias c
                                 JOIN solicitudes_cirugia s ON s.id = c.solicitud_cirugia_id
                                 JOIN alergias_paciente a ON a.paciente_id = s.paciente_id AND a.activo
                        WHERE c.id = :cirugiaId
                        """)
                .param("cirugiaId", cirugiaId)
                .query(String.class)
                .single();
    }

    private PreoperatorioResponse respuesta(DatosPreoperatoriosCirugia preop) {
        String validadoPor = preop.getValidadoPorUsuarioId() == null ? null
                : jdbc.sql("SELECT nombres || ' ' || apellidos FROM usuarios WHERE id = :id")
                        .param("id", preop.getValidadoPorUsuarioId())
                        .query(String.class)
                        .optional().orElse(null);
        return PreoperatorioResponse.de(preop, validadoPor);
    }

    private DatosPreoperatoriosCirugia buscar(UUID cirugiaId) {
        exigirCirugia(cirugiaId);
        return preoperatorios.findByCirugiaId(cirugiaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La cirugía " + cirugiaId + " no tiene datos preoperatorios"));
    }

    private void exigirCirugia(UUID cirugiaId) {
        if (!cirugias.existsById(cirugiaId)) {
            throw new RecursoNoEncontradoException("Cirugía no encontrada: " + cirugiaId);
        }
    }
}
