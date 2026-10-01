package com.hackaton.ulibre.solicitudes;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.Lateralidad;
import com.hackaton.ulibre.comun.Pagina;
import com.hackaton.ulibre.comun.Paginacion;
import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.ReglaNegocioException;
import com.hackaton.ulibre.comun.Textos;
import com.hackaton.ulibre.comun.UsuarioActual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solicitudes de cirugía y sus requerimientos de rol. Las reglas 7 y 8 y la cantidad > 0 las
 * garantiza la base; aquí solo se controlan las transiciones de estado y lo que la base no ve.
 */
@Service
public class SolicitudesService {

    private final SolicitudCirugiaRepository solicitudes;
    private final RequerimientoRolSolicitudRepository requerimientos;
    private final SolicitudesConsultas consultas;
    private final Clock clock;

    public SolicitudesService(SolicitudCirugiaRepository solicitudes,
            RequerimientoRolSolicitudRepository requerimientos, SolicitudesConsultas consultas, Clock clock) {
        this.solicitudes = solicitudes;
        this.requerimientos = requerimientos;
        this.consultas = consultas;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Pagina<SolicitudCirugiaResumen> listar(EstadoSolicitudCirugia estado, UUID pacienteId, UUID medicoId,
            UUID procedimientoId, int pagina, int tamano) {
        return consultas.listar(estado, pacienteId, medicoId, procedimientoId, Paginacion.de(pagina, tamano));
    }

    @Transactional(readOnly = true)
    public SolicitudCirugiaResponse obtener(UUID id) {
        return consultas.detalle(id).orElseThrow(() -> noEncontrada(id));
    }

    @Transactional
    public SolicitudCirugiaResponse crear(SolicitudCirugiaRequest datos) {
        Boolean procedimientoActivo = consultas.procedimientoActivo(datos.procedimientoId());
        if (procedimientoActivo == null) {
            throw new RecursoNoEncontradoException("Procedimiento no encontrado: " + datos.procedimientoId());
        }
        if (!procedimientoActivo) {
            throw new ReglaNegocioException("El procedimiento está inactivo y no admite nuevas solicitudes");
        }
        if (datos.citaOrigenId() != null) {
            UUID pacienteCita = consultas.pacienteDeCita(datos.citaOrigenId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada: " + datos.citaOrigenId()));
            if (!pacienteCita.equals(datos.pacienteId())) {
                throw new ReglaNegocioException("La cita de origen pertenece a otro paciente");
            }
        }

        SolicitudCirugia solicitud = new SolicitudCirugia();
        solicitud.setPacienteId(datos.pacienteId());
        solicitud.setMedicoSolicitanteId(datos.medicoSolicitanteId() != null ? datos.medicoSolicitanteId()
                : consultas.perfilProfesionalActivo(UsuarioActual.id()).orElseThrow(() -> new ReglaNegocioException(
                        "El usuario no tiene un perfil profesional activo: indique medicoSolicitanteId")));
        solicitud.setCitaOrigenId(datos.citaOrigenId());
        solicitud.setEspecialidadSolicitanteId(datos.especialidadSolicitanteId());
        solicitud.setProcedimientoId(datos.procedimientoId());
        aplicarDatosClinicos(solicitud, datos.sitioQuirurgico(), datos.lateralidad(), datos.resumenClinico(),
                datos.notasMedicas());
        solicitud.setEstado(EstadoSolicitudCirugia.BORRADOR);
        solicitud = solicitudes.saveAndFlush(solicitud);

        if (datos.requerimientos() == null) {
            consultas.copiarRolesPredeterminados(solicitud.getId(), solicitud.getProcedimientoId());
        } else {
            for (RequerimientoRequest req : datos.requerimientos()) {
                RequerimientoRolSolicitud nuevo = new RequerimientoRolSolicitud();
                nuevo.setSolicitudCirugiaId(solicitud.getId());
                aplicar(nuevo, req);
                requerimientos.save(nuevo);
            }
            requerimientos.flush();
        }
        return obtener(solicitud.getId());
    }

    @Transactional
    public SolicitudCirugiaResponse actualizar(UUID id, DatosClinicosRequest datos) {
        SolicitudCirugia solicitud = buscar(id);
        if (!solicitud.getEstado().admiteEdicion()) {
            throw new ReglaNegocioException("Solo se editan solicitudes en BORRADOR o ENVIADA; esta está "
                    + solicitud.getEstado());
        }
        aplicarDatosClinicos(solicitud, datos.sitioQuirurgico(), datos.lateralidad(), datos.resumenClinico(),
                datos.notasMedicas());
        solicitudes.saveAndFlush(solicitud);
        return obtener(id);
    }

    @Transactional
    public RequerimientoResponse agregarRequerimiento(UUID solicitudId, RequerimientoRequest datos) {
        exigirRequerimientosEditables(buscar(solicitudId));
        RequerimientoRolSolicitud req = new RequerimientoRolSolicitud();
        req.setSolicitudCirugiaId(solicitudId);
        aplicar(req, datos);
        req = requerimientos.saveAndFlush(req);
        return requerimiento(solicitudId, req.getId());
    }

    @Transactional
    public RequerimientoResponse actualizarRequerimiento(UUID solicitudId, UUID requerimientoId,
            RequerimientoRequest datos) {
        exigirRequerimientosEditables(buscar(solicitudId));
        RequerimientoRolSolicitud req = buscarRequerimiento(solicitudId, requerimientoId);
        int vigentes = consultas.asignacionesVigentes(requerimientoId);
        if (vigentes > 0) {
            // El trigger de asignaciones solo valida al asignar: cambiar rol o especialidad dejaría
            // personas cubriendo un requerimiento que ya no cumplen.
            if (!req.getRolClinicoId().equals(datos.rolClinicoId())
                    || !Objects.equals(req.getEspecialidadId(), datos.especialidadId())) {
                throw new ReglaNegocioException("El requerimiento tiene " + vigentes
                        + " asignaciones vigentes: no se puede cambiar su rol clínico ni su especialidad");
            }
            int cantidad = datos.cantidad() == null ? 1 : datos.cantidad();
            if (cantidad < vigentes) {
                throw new ReglaNegocioException("La cantidad (" + cantidad + ") no puede ser menor que las "
                        + vigentes + " asignaciones vigentes");
            }
        }
        aplicar(req, datos);
        requerimientos.saveAndFlush(req);
        return requerimiento(solicitudId, requerimientoId);
    }

    /** Con asignaciones (aunque estén canceladas) la FK fk_asignacion_requerimiento lo impide: 422. */
    @Transactional
    public void eliminarRequerimiento(UUID solicitudId, UUID requerimientoId) {
        exigirRequerimientosEditables(buscar(solicitudId));
        requerimientos.delete(buscarRequerimiento(solicitudId, requerimientoId));
        requerimientos.flush();
    }

    @Transactional
    public SolicitudCirugiaResponse enviar(UUID id) {
        SolicitudCirugia solicitud = buscar(id);
        exigirTransicion(solicitud, EstadoSolicitudCirugia.ENVIADA);
        if (requerimientos.findAllBySolicitudCirugiaIdOrderByCreadoEnAscIdAsc(id).isEmpty()) {
            throw new ReglaNegocioException("La solicitud debe indicar al menos un rol clínico requerido");
        }
        solicitud.setEstado(EstadoSolicitudCirugia.ENVIADA);
        solicitud.setEnviadaEn(LocalDateTime.now(clock));
        solicitudes.saveAndFlush(solicitud);
        return obtener(id);
    }

    /** revisar, aprobar, rechazar y cancelar: solo cambian el estado. */
    @Transactional
    public SolicitudCirugiaResponse cambiarEstado(UUID id, EstadoSolicitudCirugia destino) {
        SolicitudCirugia solicitud = buscar(id);
        exigirTransicion(solicitud, destino);
        solicitud.setEstado(destino);
        solicitudes.saveAndFlush(solicitud);
        return obtener(id);
    }

    private SolicitudCirugia buscar(UUID id) {
        return solicitudes.findById(id).orElseThrow(() -> noEncontrada(id));
    }

    private RequerimientoRolSolicitud buscarRequerimiento(UUID solicitudId, UUID requerimientoId) {
        return requerimientos.findById(requerimientoId)
                .filter(r -> r.getSolicitudCirugiaId().equals(solicitudId))
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Requerimiento no encontrado en la solicitud: " + requerimientoId));
    }

    private RequerimientoResponse requerimiento(UUID solicitudId, UUID requerimientoId) {
        return consultas.requerimientos(solicitudId).stream()
                .filter(r -> r.id().equals(requerimientoId))
                .findFirst()
                .orElseThrow();
    }

    private static void exigirTransicion(SolicitudCirugia solicitud, EstadoSolicitudCirugia destino) {
        if (!solicitud.getEstado().puedePasarA(destino)) {
            throw new ReglaNegocioException("La solicitud no puede pasar de " + solicitud.getEstado()
                    + " a " + destino);
        }
    }

    private static void exigirRequerimientosEditables(SolicitudCirugia solicitud) {
        if (!solicitud.getEstado().admiteCambiosEnRequerimientos()) {
            throw new ReglaNegocioException("Los requerimientos de una solicitud " + solicitud.getEstado()
                    + " no se modifican");
        }
    }

    private static void aplicarDatosClinicos(SolicitudCirugia solicitud, String sitio, Lateralidad lateralidad,
            String resumen, String notas) {
        solicitud.setSitioQuirurgico(Textos.limpiar(sitio));
        solicitud.setLateralidad(lateralidad == null ? Lateralidad.NO_APLICA : lateralidad);
        solicitud.setResumenClinico(Textos.limpiar(resumen));
        solicitud.setNotasMedicas(Textos.limpiar(notas));
    }

    private static void aplicar(RequerimientoRolSolicitud req, RequerimientoRequest datos) {
        req.setRolClinicoId(datos.rolClinicoId());
        req.setEspecialidadId(datos.especialidadId());
        req.setCantidad(datos.cantidad() == null ? 1 : datos.cantidad());
        req.setEsRequerido(datos.esRequerido() == null || datos.esRequerido());
        req.setNotas(Textos.limpiar(datos.notas()));
    }

    private static RecursoNoEncontradoException noEncontrada(UUID id) {
        return new RecursoNoEncontradoException("Solicitud de cirugía no encontrada: " + id);
    }
}
