package com.hackaton.ulibre.cirugias;

import java.util.Optional;
import java.util.UUID;

import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.ReglaNegocioException;
import com.hackaton.ulibre.comun.UsuarioActual;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * En qué calidad participa el usuario autenticado en una cirugía. Lo que se registra en el tablero
 * se firma con la asignación (persona + rol en ESA cirugía), no solo con el usuario.
 *
 * <p>TABLERO_OPERAR habilita la función; además, en cada cirugía solo opera el tablero quien tenga
 * es_operador_tablero (semilla V2). La base exige que quien firma sea personal vigente de la misma
 * cirugía, pero no que sea el operador: eso se comprueba aquí.
 */
@Component
public class ParticipacionCirugia {

    private final JdbcClient jdbc;

    public ParticipacionCirugia(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Estado actual de la cirugía; 404 si no existe. */
    public EstadoCirugia exigirCirugia(UUID cirugiaId) {
        return jdbc.sql("SELECT estado::text FROM cirugias WHERE id = :id")
                .param("id", cirugiaId)
                .query(String.class)
                .optional()
                .map(EstadoCirugia::valueOf)
                .orElseThrow(() -> new RecursoNoEncontradoException("La cirugía " + cirugiaId + " no existe"));
    }

    /** Asignación vigente (ASIGNADA o CONFIRMADA) del usuario autenticado en la cirugía. */
    public Optional<UUID> asignacionVigente(UUID cirugiaId) {
        return jdbc.sql("""
                        SELECT a.id
                        FROM asignaciones_personal_cirugia a
                                 JOIN perfiles_profesionales pp ON pp.id = a.profesional_id
                        WHERE a.cirugia_id = :cirugiaId
                          AND pp.usuario_id = :usuarioId
                          AND a.estado IN ('ASIGNADA', 'CONFIRMADA')
                        """)
                .param("cirugiaId", cirugiaId)
                .param("usuarioId", UsuarioActual.id())
                .query(UUID.class)
                .optional();
    }

    /** Para acciones que exigen participar en la cirugía (p. ej. preparar instrumental). */
    public UUID exigirAsignado(UUID cirugiaId) {
        return asignacionVigente(cirugiaId).orElseThrow(() -> new ReglaNegocioException(
                "No tiene una asignación vigente en esta cirugía"));
    }

    /** Asignación del usuario autenticado si es el operador vigente del tablero de la cirugía. */
    public UUID exigirOperador(UUID cirugiaId) {
        return jdbc.sql("""
                        SELECT a.id
                        FROM asignaciones_personal_cirugia a
                                 JOIN perfiles_profesionales pp ON pp.id = a.profesional_id
                        WHERE a.cirugia_id = :cirugiaId
                          AND pp.usuario_id = :usuarioId
                          AND a.es_operador_tablero
                          AND a.estado IN ('ASIGNADA', 'CONFIRMADA')
                        """)
                .param("cirugiaId", cirugiaId)
                .param("usuarioId", UsuarioActual.id())
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new ReglaNegocioException(
                        "Solo el operador del tablero de esta cirugía puede registrar en el tablero"));
    }
}
