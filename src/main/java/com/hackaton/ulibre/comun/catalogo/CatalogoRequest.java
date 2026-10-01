package com.hackaton.ulibre.comun.catalogo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta y edición de un catálogo simple. El código se guarda en mayúsculas. Los límites son los de
 * la columna más holgada; si una tabla admite menos, la base responde 422.
 */
public record CatalogoRequest(
        @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "solo letras, números, guion y guion bajo")
        String codigo,
        @NotBlank @Size(max = 180) String nombre,
        @Size(max = 250) String descripcion,
        Boolean activo) {
}
