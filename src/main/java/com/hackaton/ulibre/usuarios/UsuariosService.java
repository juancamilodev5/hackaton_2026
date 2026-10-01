package com.hackaton.ulibre.usuarios;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.hackaton.ulibre.auth.EstadoUsuario;
import com.hackaton.ulibre.auth.Usuario;
import com.hackaton.ulibre.auth.UsuarioRepository;
import com.hackaton.ulibre.comun.Pagina;
import com.hackaton.ulibre.comun.Paginacion;
import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.ReglaNegocioException;
import com.hackaton.ulibre.comun.Textos;
import com.hackaton.ulibre.comun.UsuarioActual;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Usuarios de la plataforma. No se borran (pueden haber firmado registros clínicos): "eliminar"
 * los deja INACTIVOS. El correo único lo garantiza uq_usuarios_correo (409).
 */
@Service
public class UsuariosService {

    static final String ROL_ADMIN = "ADMIN";

    private final UsuarioRepository usuarios;
    private final UsuariosConsultas consultas;
    private final PasswordEncoder passwordEncoder;
    private final JdbcClient jdbc;

    public UsuariosService(UsuarioRepository usuarios, UsuariosConsultas consultas, PasswordEncoder passwordEncoder,
            JdbcClient jdbc) {
        this.usuarios = usuarios;
        this.consultas = consultas;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Pagina<UsuarioResponse> listar(String q, EstadoUsuario estado, int pagina, int tamano) {
        return consultas.listar(Textos.limpiar(q), estado, Paginacion.de(pagina, tamano));
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtener(UUID id) {
        return consultas.obtener(id).orElseThrow(() -> noEncontrado(id));
    }

    @Transactional
    public UsuarioResponse crear(NuevoUsuarioRequest datos) {
        Usuario usuario = new Usuario();
        aplicar(usuario, datos.nombres(), datos.apellidos(), datos.correo(), datos.telefono());
        usuario.setEstado(EstadoUsuario.ACTIVO);
        if (datos.contrasena() != null) {
            usuario.setHashContrasena(passwordEncoder.encode(datos.contrasena()));
        }
        usuarios.saveAndFlush(usuario);
        if (datos.rolesSistema() != null) {
            asignarRoles(usuario.getId(), datos.rolesSistema());
        }
        return obtener(usuario.getId());
    }

    @Transactional
    public UsuarioResponse actualizar(UUID id, UsuarioRequest datos) {
        Usuario usuario = buscar(id);
        aplicar(usuario, datos.nombres(), datos.apellidos(), datos.correo(), datos.telefono());
        usuarios.saveAndFlush(usuario);
        return obtener(id);
    }

    @Transactional
    public UsuarioResponse cambiarEstado(UUID id, EstadoUsuario estado) {
        Usuario usuario = buscar(id);
        if (estado != EstadoUsuario.ACTIVO && id.equals(UsuarioActual.id())) {
            throw new ReglaNegocioException("No puede desactivar o suspender su propio usuario");
        }
        usuario.setEstado(estado);
        usuarios.saveAndFlush(usuario);
        return obtener(id);
    }

    @Transactional
    public void cambiarContrasena(UUID id, String contrasena) {
        Usuario usuario = buscar(id);
        usuario.setHashContrasena(passwordEncoder.encode(contrasena));
        usuarios.saveAndFlush(usuario);
    }

    @Transactional
    public UsuarioResponse reemplazarRoles(UUID id, List<String> roles) {
        buscar(id);
        asignarRoles(id, roles);
        return obtener(id);
    }

    @Transactional
    public void desactivar(UUID id) {
        cambiarEstado(id, EstadoUsuario.INACTIVO);
    }

    /** Deja exactamente los roles indicados: borra los que sobran e inserta los que faltan. */
    private void asignarRoles(UUID usuarioId, List<String> codigos) {
        Set<String> pedidos = codigos.stream().map(Textos::codigo).collect(Collectors.toCollection(LinkedHashSet::new));
        if (usuarioId.equals(UsuarioActual.id()) && !pedidos.contains(ROL_ADMIN)
                && consultas.roles(usuarioId).contains(ROL_ADMIN)) {
            throw new ReglaNegocioException("No puede quitarse a sí mismo el rol " + ROL_ADMIN);
        }

        Map<String, UUID> idsPorCodigo = pedidos.isEmpty() ? Map.of()
                : jdbc.sql("SELECT codigo, id FROM roles_sistema WHERE codigo IN (:codigos)")
                        .param("codigos", pedidos)
                        .query((rs, n) -> Map.entry(rs.getString("codigo"), rs.getObject("id", UUID.class)))
                        .list().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        List<String> inexistentes = pedidos.stream().filter(c -> !idsPorCodigo.containsKey(c)).toList();
        if (!inexistentes.isEmpty()) {
            throw new ReglaNegocioException("Roles del sistema inexistentes: " + String.join(", ", inexistentes));
        }

        if (idsPorCodigo.isEmpty()) {
            jdbc.sql("DELETE FROM usuario_roles_sistema WHERE usuario_id = :usuarioId")
                    .param("usuarioId", usuarioId)
                    .update();
            return;
        }
        jdbc.sql("DELETE FROM usuario_roles_sistema WHERE usuario_id = :usuarioId AND rol_sistema_id NOT IN (:ids)")
                .param("usuarioId", usuarioId)
                .param("ids", idsPorCodigo.values())
                .update();
        for (UUID rolId : idsPorCodigo.values()) {
            jdbc.sql("""
                            INSERT INTO usuario_roles_sistema (usuario_id, rol_sistema_id)
                            VALUES (:usuarioId, :rolId)
                            ON CONFLICT DO NOTHING
                            """)
                    .param("usuarioId", usuarioId)
                    .param("rolId", rolId)
                    .update();
        }
    }

    private Usuario buscar(UUID id) {
        return usuarios.findById(id).orElseThrow(() -> noEncontrado(id));
    }

    private static void aplicar(Usuario usuario, String nombres, String apellidos, String correo, String telefono) {
        usuario.setNombres(Textos.limpiar(nombres));
        usuario.setApellidos(Textos.limpiar(apellidos));
        usuario.setCorreo(Textos.correo(correo));
        usuario.setTelefono(Textos.limpiar(telefono));
    }

    private static RecursoNoEncontradoException noEncontrado(UUID id) {
        return new RecursoNoEncontradoException("Usuario no encontrado: " + id);
    }
}
