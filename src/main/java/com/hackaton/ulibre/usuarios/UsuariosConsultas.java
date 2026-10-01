package com.hackaton.ulibre.usuarios;

import static com.hackaton.ulibre.comun.Filas.enumeracion;
import static com.hackaton.ulibre.comun.Filas.fechaHora;
import static com.hackaton.ulibre.comun.Filas.uuid;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.hackaton.ulibre.auth.EstadoUsuario;
import com.hackaton.ulibre.comun.Pagina;
import com.hackaton.ulibre.comun.Paginacion;
import com.hackaton.ulibre.comun.Textos;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Lecturas de usuarios con sus roles, perfil profesional y paciente vinculado, en una sola consulta. */
@Component
public class UsuariosConsultas {

    private static final String SELECT = """
            SELECT u.id, u.nombres, u.apellidos, u.correo, u.telefono, u.estado::text AS estado,
                   u.hash_contrasena IS NOT NULL AS tiene_contrasena, u.creado_en, u.actualizado_en,
                   (SELECT pp.id FROM perfiles_profesionales pp WHERE pp.usuario_id = u.id) AS perfil_id,
                   (SELECT p.id FROM pacientes p WHERE p.usuario_id = u.id) AS paciente_id,
                   ARRAY(SELECT r.codigo
                         FROM usuario_roles_sistema ur
                                  JOIN roles_sistema r ON r.id = ur.rol_sistema_id
                         WHERE ur.usuario_id = u.id
                         ORDER BY r.codigo) AS roles
            FROM usuarios u
            """;

    private final JdbcClient jdbc;

    public UsuariosConsultas(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Pagina<UsuarioResponse> listar(String q, EstadoUsuario estado, Paginacion paginacion) {
        StringBuilder where = new StringBuilder(" WHERE true");
        Map<String, Object> params = new HashMap<>();
        if (q != null) {
            where.append(" AND (u.nombres ILIKE :q OR u.apellidos ILIKE :q OR u.correo ILIKE :q"
                    + " OR (u.nombres || ' ' || u.apellidos) ILIKE :q)");
            params.put("q", Textos.patronLike(q));
        }
        if (estado != null) {
            where.append(" AND u.estado = CAST(:estado AS estado_usuario)");
            params.put("estado", estado.name());
        }
        long total = jdbc.sql("SELECT count(*) FROM usuarios u" + where)
                .params(params)
                .query(Long.class)
                .single();
        List<UsuarioResponse> contenido = jdbc.sql(SELECT + where
                        + " ORDER BY u.apellidos, u.nombres, u.id LIMIT :limite OFFSET :desplazamiento")
                .params(params)
                .param("limite", paginacion.tamano())
                .param("desplazamiento", paginacion.desplazamiento())
                .query((rs, n) -> fila(rs))
                .list();
        return Pagina.de(contenido, paginacion, total);
    }

    public Optional<UsuarioResponse> obtener(UUID id) {
        return jdbc.sql(SELECT + " WHERE u.id = :id")
                .param("id", id)
                .query((rs, n) -> fila(rs))
                .optional();
    }

    /** Códigos de los roles del sistema asignados al usuario. */
    public List<String> roles(UUID usuarioId) {
        return jdbc.sql("""
                        SELECT r.codigo
                        FROM usuario_roles_sistema ur
                                 JOIN roles_sistema r ON r.id = ur.rol_sistema_id
                        WHERE ur.usuario_id = :usuarioId
                        """)
                .param("usuarioId", usuarioId)
                .query(String.class)
                .list();
    }

    private static UsuarioResponse fila(ResultSet rs) throws SQLException {
        return new UsuarioResponse(
                uuid(rs, "id"),
                rs.getString("nombres"),
                rs.getString("apellidos"),
                rs.getString("correo"),
                rs.getString("telefono"),
                enumeracion(rs, "estado", EstadoUsuario.class),
                rs.getBoolean("tiene_contrasena"),
                textos(rs.getArray("roles")),
                uuid(rs, "perfil_id"),
                uuid(rs, "paciente_id"),
                fechaHora(rs, "creado_en"),
                fechaHora(rs, "actualizado_en"));
    }

    static List<String> textos(Array arreglo) throws SQLException {
        return arreglo == null ? List.of() : List.of((String[]) arreglo.getArray());
    }
}
