package com.hackaton.ulibre.cirugias;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.hackaton.ulibre.comun.ReglaNegocioException;

/**
 * Único lugar donde se definen los cambios de estado permitidos de una cirugía: el flujo avanza de
 * a un paso y solo hacia adelante; CANCELADA y SUSPENDIDA se permiten antes de EN_CIRUGIA.
 * Lo que exige la base (instrumental completo al pasar a EN_CIRUGIA) no se repite aquí.
 */
public final class TransicionesCirugia {

    private static final List<EstadoCirugia> FLUJO = List.of(
            EstadoCirugia.PROGRAMADA, EstadoCirugia.PREPARACION, EstadoCirugia.LISTA,
            EstadoCirugia.EN_QUIROFANO, EstadoCirugia.ANESTESIA, EstadoCirugia.EN_CIRUGIA,
            EstadoCirugia.CIERRE, EstadoCirugia.RECUPERACION, EstadoCirugia.COMPLETADA);

    private static final Set<EstadoCirugia> TERMINALES =
            EnumSet.of(EstadoCirugia.CANCELADA, EstadoCirugia.SUSPENDIDA);

    /** Estados en los que aún se puede cambiar quirófano y horario. */
    public static final Set<EstadoCirugia> REPROGRAMABLES =
            EnumSet.of(EstadoCirugia.PROGRAMADA, EstadoCirugia.PREPARACION, EstadoCirugia.LISTA);

    private TransicionesCirugia() {
    }

    public static boolean esTerminal(EstadoCirugia estado) {
        return TERMINALES.contains(estado);
    }

    public static void validar(EstadoCirugia actual, EstadoCirugia nuevo) {
        if (!permitida(actual, nuevo)) {
            throw new ReglaNegocioException("Transición de estado no permitida: " + actual + " → " + nuevo);
        }
    }

    private static boolean permitida(EstadoCirugia actual, EstadoCirugia nuevo) {
        int posicionActual = FLUJO.indexOf(actual);
        if (posicionActual < 0) {
            return false;   // CANCELADA o SUSPENDIDA: no se reprograma
        }
        if (TERMINALES.contains(nuevo)) {
            return posicionActual < FLUJO.indexOf(EstadoCirugia.EN_CIRUGIA);
        }
        return FLUJO.indexOf(nuevo) == posicionActual + 1;
    }
}
