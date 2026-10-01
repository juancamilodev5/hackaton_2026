package com.hackaton.ulibre.profesionales;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.hackaton.ulibre.auditoria.AccionAuditoria;
import com.hackaton.ulibre.auditoria.Auditoria;
import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bloques de disponibilidad explícita. Son planificación, no histórico clínico: se borran
 * físicamente. Cambiarlos no afecta asignaciones ya hechas (la base solo valida al asignar).
 * Cada cambio (también el borrado) queda en registros_auditoria.
 */
@Service
public class DisponibilidadService {

    /** Límites para consultas sin rango: el rango abierto de un lado se cierra con estos valores. */
    private static final LocalDateTime SIN_INICIO = LocalDateTime.of(1900, 1, 1, 0, 0);
    private static final LocalDateTime SIN_FIN = LocalDateTime.of(9999, 12, 31, 0, 0);
    private static final String TABLA = "disponibilidad_profesional";

    private final DisponibilidadProfesionalRepository disponibilidades;
    private final ProfesionalesService profesionales;
    private final Auditoria auditoria;

    public DisponibilidadService(DisponibilidadProfesionalRepository disponibilidades,
            ProfesionalesService profesionales, Auditoria auditoria) {
        this.disponibilidades = disponibilidades;
        this.profesionales = profesionales;
        this.auditoria = auditoria;
    }

    /** Bloques que se solapan con [desde, hasta); sin rango, todos. */
    @Transactional(readOnly = true)
    public List<DisponibilidadResponse> listar(UUID profesionalId, LocalDateTime desde, LocalDateTime hasta) {
        profesionales.buscar(profesionalId);
        return disponibilidades.findAllByProfesionalIdAndTerminaEnAfterAndIniciaEnBeforeOrderByIniciaEn(
                        profesionalId, desde == null ? SIN_INICIO : desde, hasta == null ? SIN_FIN : hasta)
                .stream()
                .map(DisponibilidadResponse::de)
                .toList();
    }

    @Transactional
    public DisponibilidadResponse crear(UUID profesionalId, DisponibilidadRequest datos) {
        profesionales.buscar(profesionalId);
        DisponibilidadProfesional bloque = new DisponibilidadProfesional();
        bloque.setProfesionalId(profesionalId);
        aplicar(bloque, datos);
        DisponibilidadResponse creado = DisponibilidadResponse.de(disponibilidades.saveAndFlush(bloque));
        auditoria.registrar(TABLA, creado.id(), AccionAuditoria.CREAR, null, creado);
        return creado;
    }

    @Transactional
    public DisponibilidadResponse actualizar(UUID profesionalId, UUID id, DisponibilidadRequest datos) {
        DisponibilidadProfesional bloque = buscar(profesionalId, id);
        DisponibilidadResponse anterior = DisponibilidadResponse.de(bloque);
        aplicar(bloque, datos);
        DisponibilidadResponse actualizado = DisponibilidadResponse.de(disponibilidades.saveAndFlush(bloque));
        auditoria.registrar(TABLA, id, AccionAuditoria.ACTUALIZAR, anterior, actualizado);
        return actualizado;
    }

    @Transactional
    public void eliminar(UUID profesionalId, UUID id) {
        DisponibilidadProfesional bloque = buscar(profesionalId, id);
        DisponibilidadResponse anterior = DisponibilidadResponse.de(bloque);
        disponibilidades.delete(bloque);
        disponibilidades.flush();
        auditoria.registrar(TABLA, id, AccionAuditoria.ELIMINAR, anterior, null);
    }

    private DisponibilidadProfesional buscar(UUID profesionalId, UUID id) {
        return disponibilidades.findByIdAndProfesionalId(id, profesionalId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Disponibilidad no encontrada para el profesional: " + id));
    }

    private static void aplicar(DisponibilidadProfesional bloque, DisponibilidadRequest datos) {
        bloque.setTipoDisponibilidad(datos.tipoDisponibilidad());
        bloque.setIniciaEn(datos.iniciaEn());
        bloque.setTerminaEn(datos.terminaEn());
        bloque.setNotas(Textos.limpiar(datos.notas()));
    }
}
