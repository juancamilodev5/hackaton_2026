package com.hackaton.ulibre.tablero;

import java.util.List;

import com.hackaton.ulibre.alertas.AlertaVista;
import com.hackaton.ulibre.checklist.ChecklistVista;
import com.hackaton.ulibre.cirugias.CirugiaVista;
import com.hackaton.ulibre.cirugias.EquipoVista;
import com.hackaton.ulibre.cirugias.OperadorTableroVista;
import com.hackaton.ulibre.cirugias.PacienteVista;
import com.hackaton.ulibre.cirugias.PreoperatorioVista;
import com.hackaton.ulibre.cirugias.ProcedimientoVista;
import com.hackaton.ulibre.instrumental.InstrumentalVista;

/** Todo lo que necesita la pantalla del tablero de seguridad quirúrgica, en una sola respuesta. */
public record TableroVista(
        CirugiaVista cirugia,
        PacienteVista paciente,
        ProcedimientoVista procedimiento,
        /** null si aún no hay datos preoperatorios. */
        PreoperatorioVista preoperatorio,
        EquipoVista equipo,
        /** null si no hay operador del tablero vigente. */
        OperadorTableroVista operadorTablero,
        /** null si el checklist no se ha iniciado. */
        ChecklistVista checklist,
        InstrumentalVista instrumental,
        List<RecuentoVista> recuentos,
        List<AlertaVista> alertas,
        List<HitoVista> hitos) {
}
