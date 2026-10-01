package com.hackaton.ulibre.tablero;

import java.util.Map;
import java.util.UUID;

import com.hackaton.ulibre.alertas.AlertasConsultas;
import com.hackaton.ulibre.checklist.ChecklistConsultas;
import com.hackaton.ulibre.cirugias.CabeceraCirugia;
import com.hackaton.ulibre.cirugias.CirugiasConsultas;
import com.hackaton.ulibre.cirugias.EquipoVista;
import com.hackaton.ulibre.cirugias.OperadorTableroVista;
import com.hackaton.ulibre.cirugias.ParticipanteVista;
import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.instrumental.InstrumentalConsultas;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arma el tablero con un número fijo de consultas (sin N+1), sin importar cuántas fases, ítems,
 * sets o alertas tenga la cirugía.
 */
@Service
public class TableroService {

    private final CirugiasConsultas cirugias;
    private final ChecklistConsultas checklist;
    private final InstrumentalConsultas instrumental;
    private final AlertasConsultas alertas;
    private final TableroConsultas tablero;

    public TableroService(CirugiasConsultas cirugias, ChecklistConsultas checklist,
            InstrumentalConsultas instrumental, AlertasConsultas alertas,
            TableroConsultas tablero) {
        this.cirugias = cirugias;
        this.checklist = checklist;
        this.instrumental = instrumental;
        this.alertas = alertas;
        this.tablero = tablero;
    }

    /** REPEATABLE READ: todas las consultas ven la misma foto aunque alguien registre algo a la vez. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TableroVista tablero(UUID cirugiaId) {
        CabeceraCirugia cabecera = cirugias.cabecera(cirugiaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La cirugía " + cirugiaId + " no existe"));

        Map<UUID, ParticipanteVista> participantes = cirugias.participantes(cirugiaId);
        EquipoVista equipo = cirugias.equipo(cirugiaId);
        OperadorTableroVista operador = equipo.requerimientos().stream()
                .flatMap(r -> r.asignaciones().stream())
                .filter(a -> a.esOperadorTablero())
                .findFirst()
                .map(a -> new OperadorTableroVista(a.asignacionId(), a.profesional()))
                .orElse(null);

        return new TableroVista(
                cabecera.cirugia(),
                cabecera.paciente(),
                cabecera.procedimiento(),
                cabecera.preoperatorio(),
                equipo,
                operador,
                checklist.deCirugia(cirugiaId, participantes),
                instrumental.deCirugia(cirugiaId, participantes),
                tablero.recuentos(cirugiaId, participantes),
                alertas.deCirugia(cirugiaId),
                tablero.hitos(cirugiaId, participantes));
    }
}
