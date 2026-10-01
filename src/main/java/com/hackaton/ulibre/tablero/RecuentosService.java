package com.hackaton.ulibre.tablero;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recuentos: inicial → material agregado → final → confirmaciones por etapa. Quien digita es el
 * operador del tablero; quien confirma es la persona (asignación) que hizo el conteo. Esperada y
 * estado (PENDIENTE/CUADRA/DISCREPANCIA) los calcula la vista v_recuentos_cirugia; la regla
 * RECUENTO_INCONSISTENTE genera la alerta.
 */
@Service
public class RecuentosService {

    private static final Set<EstadoCirugia> CERRADAS =
            EnumSet.of(EstadoCirugia.CANCELADA, EstadoCirugia.SUSPENDIDA, EstadoCirugia.COMPLETADA);

    private final RecuentoCirugiaRepository recuentos;
    private final ConfirmacionRecuentoRepository confirmaciones;
    private final TableroConsultas consultas;
    private final CirugiasConsultas cirugias;
    private final ParticipacionCirugia participacion;
    private final EventosCirugia eventos;
    private final MotorReglas motor;
    private final Clock clock;

    public RecuentosService(RecuentoCirugiaRepository recuentos, ConfirmacionRecuentoRepository confirmaciones,
            TableroConsultas consultas, CirugiasConsultas cirugias, ParticipacionCirugia participacion,
            EventosCirugia eventos, MotorReglas motor, Clock clock) {
        this.recuentos = recuentos;
        this.confirmaciones = confirmaciones;
        this.consultas = consultas;
        this.cirugias = cirugias;
        this.participacion = participacion;
        this.eventos = eventos;
        this.motor = motor;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RecuentoVista> listar(UUID cirugiaId) {
        participacion.exigirCirugia(cirugiaId);
        return consultas.recuentos(cirugiaId, cirugias.participantes(cirugiaId));
    }

    @Transactional
    public RecuentoVista registrarInicial(UUID cirugiaId, RecuentoInicialRequest datos) {
        exigirAbierta(cirugiaId);
        UUID operador = participacion.exigirOperador(cirugiaId);
        RecuentoCirugia recuento = new RecuentoCirugia();
        recuento.setCirugiaId(cirugiaId);
        recuento.setTipoRecuento(datos.tipoRecuento());
        recuento.setDescripcion(Textos.limpiar(datos.descripcion()));
        recuento.setInstrumentoCirugiaId(datos.instrumentoCirugiaId());
        recuento.setCantidadInicial(datos.cantidadInicial());
        recuento.setCantidadAgregada(0);   // Hibernate no aplica el DEFAULT de la base
        recuento.setRegistradoInicialPorAsignacionId(operador);
        recuento.setRegistradoInicialEn(LocalDateTime.now(clock));
        recuento.setNotas(Textos.limpiar(datos.notas()));
        recuentos.saveAndFlush(recuento);
        Map<String, Object> detalle = new HashMap<>();
        detalle.put("tipo", recuento.getTipoRecuento().name());
        detalle.put("cantidadInicial", recuento.getCantidadInicial());
        if (recuento.getDescripcion() != null) {
            detalle.put("descripcion", recuento.getDescripcion());
        }
        eventos.registrar(cirugiaId, TipoEvento.RECUENTO_INICIAL_REGISTRADO, operador, "recuentos_cirugia",
                recuento.getId(), detalle);
        return vista(cirugiaId, recuento.getId());
    }

    @Transactional
    public RecuentoVista agregarMaterial(UUID cirugiaId, UUID recuentoId, MaterialAgregadoRequest datos) {
        exigirAbierta(cirugiaId);
        UUID operador = participacion.exigirOperador(cirugiaId);
        RecuentoCirugia recuento = buscar(cirugiaId, recuentoId);
        if (recuento.getCantidadFinal() != null) {
            throw new ReglaNegocioException("El conteo final ya está registrado: no se puede agregar material");
        }
        recuento.setCantidadAgregada(recuento.getCantidadAgregada() + datos.cantidad());
        if (Textos.limpiar(datos.notas()) != null) {
            recuento.setNotas(Textos.limpiar(datos.notas()));
        }
        recuentos.saveAndFlush(recuento);
        eventos.registrar(cirugiaId, TipoEvento.RECUENTO_MATERIAL_AGREGADO, operador, "recuentos_cirugia",
                recuentoId, Map.of("cantidad", datos.cantidad(), "cantidadAgregada", recuento.getCantidadAgregada()));
        return vista(cirugiaId, recuentoId);
    }

    @Transactional
    public RecuentoVista registrarFinal(UUID cirugiaId, UUID recuentoId, RecuentoFinalRequest datos) {
        exigirAbierta(cirugiaId);
        UUID operador = participacion.exigirOperador(cirugiaId);
        RecuentoCirugia recuento = buscar(cirugiaId, recuentoId);
        if (confirmaciones.existsByRecuentoCirugiaIdAndEtapa(recuentoId, EtapaRecuento.FINAL)) {
            throw new ReglaNegocioException("El conteo final ya fue confirmado: no se puede volver a registrar");
        }
        recuento.setCantidadFinal(datos.cantidadFinal());
        recuento.setRegistradoFinalPorAsignacionId(operador);
        recuento.setRegistradoFinalEn(LocalDateTime.now(clock));
        if (Textos.limpiar(datos.notas()) != null) {
            recuento.setNotas(Textos.limpiar(datos.notas()));
        }
        recuentos.saveAndFlush(recuento);
        RecuentoVista vista = vista(cirugiaId, recuentoId);
        eventos.registrar(cirugiaId, TipoEvento.RECUENTO_FINAL_REGISTRADO, operador, "recuentos_cirugia",
                recuentoId, Map.of("cantidadFinal", vista.cantidadFinal(), "cantidadEsperada", vista.cantidadEsperada(),
                        "estado", vista.estado().name()));
        motor.evaluarAlConfirmar(cirugiaId);
        return vista;
    }

    @Transactional
    public RecuentoVista confirmar(UUID cirugiaId, UUID recuentoId, ConfirmacionRecuentoRequest datos) {
        exigirAbierta(cirugiaId);
        RecuentoCirugia recuento = buscar(cirugiaId, recuentoId);
        if (datos.etapa() == EtapaRecuento.FINAL && recuento.getCantidadFinal() == null) {
            throw new ReglaNegocioException("Registre el conteo final antes de confirmarlo");
        }
        UUID confirmador = quienConfirma(cirugiaId, datos.asignacionId());
        ConfirmacionRecuento confirmacion = new ConfirmacionRecuento();
        confirmacion.setCirugiaId(cirugiaId);
        confirmacion.setRecuentoCirugiaId(recuentoId);
        confirmacion.setEtapa(datos.etapa());
        confirmacion.setAsignacionPersonalId(confirmador);
        confirmacion.setConfirmadoEn(LocalDateTime.now(clock));
        confirmacion.setNotas(Textos.limpiar(datos.notas()));
        confirmaciones.saveAndFlush(confirmacion);
        eventos.registrar(cirugiaId, TipoEvento.RECUENTO_CONFIRMADO, confirmador, "confirmaciones_recuento",
                confirmacion.getId(), Map.of("recuentoId", recuentoId, "etapa", datos.etapa().name()));
        return vista(cirugiaId, recuentoId);
    }

    /**
     * Quien confirma: la asignación indicada o la del propio usuario. Registrar en nombre de otra
     * persona solo lo puede hacer el operador del tablero. Que la asignación sea de esta cirugía y
     * esté vigente lo valida la base (FK compuesta y trigger).
     */
    private UUID quienConfirma(UUID cirugiaId, UUID asignacionId) {
        Optional<UUID> propia = participacion.asignacionVigente(cirugiaId);
        if (asignacionId == null) {
            return propia.orElseThrow(() -> new ReglaNegocioException(
                    "No tiene una asignación vigente en esta cirugía: indique asignacionId de quien confirma"));
        }
        if (propia.filter(asignacionId::equals).isEmpty()) {
            participacion.exigirOperador(cirugiaId);
        }
        return asignacionId;
    }

    private RecuentoCirugia buscar(UUID cirugiaId, UUID recuentoId) {
        return recuentos.findById(recuentoId)
                .filter(r -> r.getCirugiaId().equals(cirugiaId))
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El recuento " + recuentoId + " no pertenece a la cirugía " + cirugiaId));
    }

    private void exigirAbierta(UUID cirugiaId) {
        EstadoCirugia estado = participacion.exigirCirugia(cirugiaId);
        if (CERRADAS.contains(estado)) {
            throw new ReglaNegocioException("La cirugía está " + estado + ": no admite recuentos");
        }
    }

    private RecuentoVista vista(UUID cirugiaId, UUID recuentoId) {
        return consultas.recuentos(cirugiaId, cirugias.participantes(cirugiaId)).stream()
                .filter(r -> r.id().equals(recuentoId))
                .findFirst()
                .orElseThrow();
    }
}
