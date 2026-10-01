package com.hackaton.ulibre.profesionales;

import java.util.UUID;

/** Rol clínico o especialidad de un profesional. */
public record Referencia(UUID id, String codigo, String nombre) {
}
