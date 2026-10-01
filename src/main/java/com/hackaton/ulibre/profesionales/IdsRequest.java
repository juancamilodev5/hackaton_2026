package com.hackaton.ulibre.profesionales;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Conjunto completo de ids (roles clínicos o especialidades) que debe quedar asignado. */
public record IdsRequest(@NotNull List<@NotNull UUID> ids) {
}
