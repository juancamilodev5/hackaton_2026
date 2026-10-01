package com.hackaton.ulibre.auth;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class AccesoConsultas {

    private final JdbcClient jdbc;

    public AccesoConsultas(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** usuario_roles_sistema → rol_sistema_permisos → permisos, solo roles activos. */
    public Acceso de(UUID usuarioId) {
        SortedSet<String> roles = new TreeSet<>();
        SortedSet<String> permisos = new TreeSet<>();
        jdbc.sql("""
                        SELECT r.codigo AS rol, p.codigo AS permiso
                        FROM usuario_roles_sistema ur
                                 JOIN roles_sistema r ON r.id = ur.rol_sistema_id AND r.activo
                                 LEFT JOIN rol_sistema_permisos rp ON rp.rol_sistema_id = r.id
                                 LEFT JOIN permisos p ON p.id = rp.permiso_id
                        WHERE ur.usuario_id = :usuarioId
                        """)
                .param("usuarioId", usuarioId)
                .query(rs -> {
                    roles.add(rs.getString("rol"));
                    String permiso = rs.getString("permiso");
                    if (permiso != null) {
                        permisos.add(permiso);
                    }
                });
        return new Acceso(List.copyOf(roles), List.copyOf(permisos));
    }
}
