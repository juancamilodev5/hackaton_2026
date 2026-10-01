package com.hackaton.ulibre.profesionales;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bloques de disponibilidad explícita. Son planificación, no histórico clínico: se borran
 * físicamente. Cambiarlos no afecta asignaciones ya hechas (la base solo valida al asignar).
 */
@Service
public class DisponibilidadService {

    /** Límites para consultas sin rango: el rango abierto de un lado se cierra con estos valores. */
    private static final LocalDateTime SIN_INICIO = LocalDateTime.of(1900, 1, 1, 0, 0);
    private static final LocalDateTime SIN_FIN = LocalDateTime.of(9999, 12, 31, 0, 0);

    private final DisponibilidadProfesionalRepository disponibilidades;
    private final ProfesionalesService profesionales;

    public DisponibilidadService(DisponibilidadProfesionalRepository disponibilidades,
            ProfesionalesService profesionales) {
        this.disponibilidades = disponibilidades;
        this.profesionales = profesionales;
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
        return DisponibilidadResponse.de(disponibilidades.saveAndFlush(bloque));
    }

    @Transactional
    public DisponibilidadResponse actualizar(UUID profesionalId, UUID id, DisponibilidadRequest datos) {
        DisponibilidadProfesional bloque = buscar(profesionalId, id);
        aplicar(bloque, datos);
        return DisponibilidadResponse.de(disponibilidades.saveAndFlush(bloque));
    }

    @Transactional
    public void eliminar(UUID profesionalId, UUID id) {
        disponibilidades.delete(buscar(profesionalId, id));
        disponibilidades.flush();
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
