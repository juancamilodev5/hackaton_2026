package com.hackaton.ulibre.catalogos;

import java.sql.ResultSet;
import java.sql.SQLException;

public record RolClinicoVista(String codigo, String nombre) {

    /** null si la columna de código viene vacía (LEFT JOIN sin coincidencia). */
    public static RolClinicoVista de(ResultSet rs, String columnaCodigo, String columnaNombre) throws SQLException {
        String codigo = rs.getString(columnaCodigo);
        return codigo == null ? null : new RolClinicoVista(codigo, rs.getString(columnaNombre));
    }
}
