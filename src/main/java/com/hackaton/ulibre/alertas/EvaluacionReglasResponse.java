package com.hackaton.ulibre.alertas;

import java.util.List;

/** Resultado de reevaluar las reglas y alertas de la cirugía tras la evaluación (vigentes primero). */
public record EvaluacionReglasResponse(int alertasGeneradas, int alertasResueltas, List<AlertaVista> alertas) {
}
