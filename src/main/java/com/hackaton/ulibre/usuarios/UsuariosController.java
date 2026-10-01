package com.hackaton.ulibre.usuarios;

import java.net.URI;
import java.util.UUID;

import com.hackaton.ulibre.auth.EstadoUsuario;
import com.hackaton.ulibre.comun.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios")
public class UsuariosController {

    private final UsuariosService servicio;

    public UsuariosController(UsuariosService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USUARIOS_VER')")
    @Operation(summary = "Usuarios paginados (q busca en nombres, apellidos y correo)")
    public Pagina<UsuarioResponse> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) EstadoUsuario estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(q, estado, pagina, tamano);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USUARIOS_VER')")
    @Operation(summary = "Usuario por id, con roles del sistema, perfil profesional y paciente vinculado")
    public UsuarioResponse obtener(@PathVariable UUID id) {
        return servicio.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USUARIOS_GESTIONAR')")
    @Operation(summary = "Crea un usuario (contraseña y roles del sistema opcionales)")
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody NuevoUsuarioRequest datos) {
        UsuarioResponse creado = servicio.crear(datos);
        return ResponseEntity.created(URI.create("/api/usuarios/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USUARIOS_GESTIONAR')")
    @Operation(summary = "Actualiza los datos básicos de un usuario")
    public UsuarioResponse actualizar(@PathVariable UUID id, @Valid @RequestBody UsuarioRequest datos) {
        return servicio.actualizar(id, datos);
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('USUARIOS_GESTIONAR')")
    @Operation(summary = "Cambia el estado (ACTIVO, INACTIVO, SUSPENDIDO); nadie puede desactivarse a sí mismo")
    public UsuarioResponse cambiarEstado(@PathVariable UUID id, @Valid @RequestBody EstadoUsuarioRequest datos) {
        return servicio.cambiarEstado(id, datos.estado());
    }

    @PutMapping("/{id}/contrasena")
    @PreAuthorize("hasAuthority('USUARIOS_GESTIONAR')")
    @Operation(summary = "Establece la contraseña de un usuario")
    public ResponseEntity<Void> cambiarContrasena(@PathVariable UUID id, @Valid @RequestBody ContrasenaRequest datos) {
        servicio.cambiarContrasena(id, datos.contrasena());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('USUARIOS_GESTIONAR')")
    @Operation(summary = "Reemplaza el conjunto de roles del sistema (por código)")
    public UsuarioResponse reemplazarRoles(@PathVariable UUID id, @Valid @RequestBody RolesUsuarioRequest datos) {
        return servicio.reemplazarRoles(id, datos.roles());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USUARIOS_GESTIONAR')")
    @Operation(summary = "Desactiva un usuario (estado INACTIVO; no se borra: puede tener histórico)")
    public ResponseEntity<Void> desactivar(@PathVariable UUID id) {
        servicio.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
