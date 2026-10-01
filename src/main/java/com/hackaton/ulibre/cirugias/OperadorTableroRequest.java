package com.hackaton.ulibre.cirugias;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record OperadorTableroRequest(@NotNull UUID asignacionId) {
}
