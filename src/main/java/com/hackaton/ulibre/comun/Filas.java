package com.hackaton.ulibre.comun;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Lectura tipada de columnas en los RowMapper de las consultas nativas. */
public final class Filas {

    private Filas() {
    }

    public static UUID uuid(ResultSet rs, String columna) throws SQLException {
        return rs.getObject(columna, UUID.class);
    }

    public static LocalDateTime fechaHora(ResultSet rs, String columna) throws SQLException {
        return rs.getObject(columna, LocalDateTime.class);
    }

    public static LocalDate fecha(ResultSet rs, String columna) throws SQLException {
        return rs.getObject(columna, LocalDate.class);
    }

    public static Integer entero(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor;
    }

    /** Las consultas leen los enums de PostgreSQL con {@code ::text}. */
    public static <E extends Enum<E>> E enumeracion(ResultSet rs, String columna, Class<E> tipo) throws SQLException {
        String valor = rs.getString(columna);
        return valor == null ? null : Enum.valueOf(tipo, valor);
    }
}
