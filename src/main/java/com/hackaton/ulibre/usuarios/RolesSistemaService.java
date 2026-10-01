package com.hackaton.ulibre.usuarios;

import static com.hackaton.ulibre.comun.Filas.uuid;
import static com.hackaton.ulibre.usuarios.UsuariosService.ROL_ADMIN;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.hackaton.ulibre.auditoria.AccionAuditoria;
import com.hackaton.ulibre.auditoria.Auditoria;
import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.ReglaNegocioException;
import com.hackaton.ulibre.comun.Textos;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Roles del sistema y sus permisos. ADMIN no se desactiva, no cambia de código y no pierde
 * permisos: el sistema no puede quedar sin nadie que lo administre. Cada cambio queda en
 * registros_auditoria.
 */
@Service
public class RolesSistemaService {

    private static final String TABLA = "roles_sistema";

    private static final String SELECT_ROL = """
            SELECT r.id, r.codigo, r.nombre, r.descripcion, r.activo,
                   ARRAY(SELECT p.codigo
                         FROM rol_sistema_permisos rp
                                  JOIN permisos p ON p.id = rp.permiso_id
                         WHERE rp.rol_sistema_id = r.id
                         ORDER BY p.codigo) AS permisos
            FROM roles_sistema r
            """;

    private final RolSistemaRepository roles;
    private final JdbcClient jdbc;
    private final Auditoria auditoria;

    public RolesSistemaService(RolSistemaRepository roles, JdbcClient jdbc, Auditoria auditoria) {
        this.roles = roles;
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<RolSistemaResponse> listar() {
        return jdbc.sql(SELECT_ROL + " ORDER BY r.nombre")
                .query((rs, n) -> fila(rs))
                .list();
    }

    @Transactional(readOnly = true)
    public RolSistemaResponse obtener(UUID id) {
        return jdbc.sql(SELECT_ROL + " WHERE r.id = :id")
                .param("id", id)
                .query((rs, n) -> fila(rs))
                .optional()
                .orElseThrow(() -> noEncontrado(id));
    }

    @Transactional(readOnly = true)
    public List<PermisoResponse> permisos() {
        return jdbc.sql("SELECT id, codigo, nombre, descripcion FROM permisos ORDER BY codigo")
                .query((rs, n) -> new PermisoResponse(uuid(rs, "id"), rs.getString("codigo"),
                        rs.getString("nombre"), rs.getString("descripcion")))
                .list();
    }

    @Transactional
    public RolSistemaResponse crear(RolSistemaRequest datos) {
        RolSistema rol = new RolSistema();
        rol.setActivo(true);
        aplicar(rol, datos);
        roles.saveAndFlush(rol);
        RolSistemaResponse creado = obtener(rol.getId());
        auditoria.registrar(TABLA, creado.id(), AccionAuditoria.CREAR, null, creado);
        return creado;
    }

    @Transactional
    public RolSistemaResponse actualizar(UUID id, RolSistemaRequest datos) {
        RolSistema rol = buscar(id);
        if (ROL_ADMIN.equals(rol.getCodigo())
                && (!ROL_ADMIN.equals(Textos.codigo(datos.codigo())) || Boolean.FALSE.equals(datos.activo()))) {
            throw new ReglaNegocioException("El rol " + ROL_ADMIN + " no puede cambiar de código ni desactivarse");
        }
        RolSistemaResponse anterior = obtener(id);
        aplicar(rol, datos);
        roles.saveAndFlush(rol);
        RolSistemaResponse actualizado = obtener(id);
        auditoria.registrar(TABLA, id, AccionAuditoria.ACTUALIZAR, anterior, actualizado);
        return actualizado;
    }

    @Transactional
    public void desactivar(UUID id) {
        RolSistema rol = buscar(id);
        if (ROL_ADMIN.equals(rol.getCodigo())) {
            throw new ReglaNegocioException("El rol " + ROL_ADMIN + " no puede desactivarse");
        }
        RolSistemaResponse anterior = obtener(id);
        rol.setActivo(false);
        roles.saveAndFlush(rol);
        auditoria.registrar(TABLA, id, AccionAuditoria.DESACTIVAR, anterior, obtener(id));
    }

    /** Deja exactamente los permisos indicados: borra los que sobran e inserta los que faltan. */
    @Transactional
    public RolSistemaResponse reemplazarPermisos(UUID id, List<String> codigos) {
        RolSistema rol = buscar(id);
        Set<String> pedidos = codigos.stream().map(Textos::codigo).collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, UUID> idsPorCodigo = pedidos.isEmpty() ? Map.of()
                : jdbc.sql("SELECT codigo, id FROM permisos WHERE codigo IN (:codigos)")
                        .param("codigos", pedidos)
                        .query((rs, n) -> Map.entry(rs.getString("codigo"), uuid(rs, "id")))
                        .list().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        List<String> inexistentes = pedidos.stream().filter(c -> !idsPorCodigo.containsKey(c)).toList();
        if (!inexistentes.isEmpty()) {
            throw new ReglaNegocioException("Permisos inexistentes: " + String.join(", ", inexistentes));
        }
        List<String> anteriores = obtener(id).permisos();
        if (ROL_ADMIN.equals(rol.getCodigo()) && !pedidos.containsAll(anteriores)) {
            throw new ReglaNegocioException("Al rol " + ROL_ADMIN + " no se le pueden quitar permisos");
        }

        if (idsPorCodigo.isEmpty()) {
            jdbc.sql("DELETE FROM rol_sistema_permisos WHERE rol_sistema_id = :rolId")
                    .param("rolId", id)
                    .update();
        } else {
            jdbc.sql("DELETE FROM rol_sistema_permisos WHERE rol_sistema_id = :rolId AND permiso_id NOT IN (:ids)")
                    .param("rolId", id)
                    .param("ids", idsPorCodigo.values())
                    .update();
            for (UUID permisoId : idsPorCodigo.values()) {
                jdbc.sql("""
                                INSERT INTO rol_sistema_permisos (rol_sistema_id, permiso_id)
                                VALUES (:rolId, :permisoId)
                                ON CONFLICT DO NOTHING
                                """)
                        .param("rolId", id)
                        .param("permisoId", permisoId)
                        .update();
            }
        }
        RolSistemaResponse actualizado = obtener(id);
        auditoria.registrar("rol_sistema_permisos", id, AccionAuditoria.REEMPLAZAR, Map.of("permisos", anteriores),
                Map.of("permisos", actualizado.permisos()));
        return actualizado;
    }

    private static RolSistemaResponse fila(ResultSet rs) throws SQLException {
        return new RolSistemaResponse(uuid(rs, "id"), rs.getString("codigo"), rs.getString("nombre"),
                rs.getString("descripcion"), rs.getBoolean("activo"), UsuariosConsultas.textos(rs.getArray("permisos")));
    }

    private RolSistema buscar(UUID id) {
        return roles.findById(id).orElseThrow(() -> noEncontrado(id));
    }

    private static void aplicar(RolSistema rol, RolSistemaRequest datos) {
        rol.setCodigo(Textos.codigo(datos.codigo()));
        rol.setNombre(Textos.limpiar(datos.nombre()));
        rol.setDescripcion(Textos.limpiar(datos.descripcion()));
        if (datos.activo() != null) {
            rol.setActivo(datos.activo());
        }
    }

    private static RecursoNoEncontradoException noEncontrado(UUID id) {
        return new RecursoNoEncontradoException("Rol del sistema no encontrado: " + id);
    }
}
