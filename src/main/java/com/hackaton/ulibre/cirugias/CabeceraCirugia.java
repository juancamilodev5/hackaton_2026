package com.hackaton.ulibre.cirugias;

/** Datos de una sola fila por cirugía que necesita el tablero. preoperatorio puede ser null. */
public record CabeceraCirugia(
        CirugiaVista cirugia,
        PacienteVista paciente,
        ProcedimientoVista procedimiento,
        PreoperatorioVista preoperatorio) {
}
