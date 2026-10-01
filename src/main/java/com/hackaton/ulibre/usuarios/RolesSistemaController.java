package com.hackaton.ulibre.usuarios;

import java.net.URI;
import java.util.List;
import java.util.UUID;

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
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Usuarios")
public class RolesSistemaController {

    private final RolesSistemaService servicio;

    public RolesSistemaController(RolesSistemaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/api/roles-sistema")
    @PreAuthorize("hasAuthority('USUARIOS_VER')")
    @Operation(summary = "Roles del sistema con sus permisos")
    public List<RolSistemaResponse> listar() {
        return servicio.listar();
    }

    @GetMapping("/api/roles-sistema/{id}")
    @PreAuthorize("hasAuthority('USUARIOS_VER')")
    @Operation(summary = "Rol del sistema por id, con sus permisos")
    public RolSistemaResponse obtener(@PathVariable UUID id) {
        return servicio.obtener(id);
    }

    @PostMapping("/api/roles-sistema")
    @PreAuthorize("hasAuthority('USUARIOS_GESTIONAR')")
    @Operation(summary = "Crea un rol del sistema (sin permisos; se asignan aparte)")
    public ResponseEntity<RolSistemaResponse> crear(@Valid @RequestBody RolSistemaRequest datos) {
        RolSistemaResponse creado = servicio.crear(datos);
        return ResponseEntity.created(URI.create("/api/roles-sistema/" + creado.id())).body(creado);
    }

    @PutMapping("/api/roles-sistema/{id}")
    @PreAuthorize("hasAuthority('USUARIOS_GESTIONAR')")
    @Operation(summary = "Actualiza un rol del sistema")
    public RolSistemaResponse actualizar(@PathVariable UUID id, @Valid @RequestBody RolSistemaRequest datos) {
        return servicio.actualizar(id, datos);
    }

    @PutMapping("/api/roles-sistema/{id}/permisos")
    @PreAuthorize("hasAuthority('USUARIOS_GESTIONAR')")
    @Operation(summary = "Reemplaza el conjunto de permisos del rol (por código)")
    public RolSistemaResponse reemplazarPermisos(@PathVariable UUID id, @Valid @RequestBody PermisosRolRequest datos) {
        return servicio.reemplazarPermisos(id, datos.permisos());
    }

    @DeleteMapping("/api/roles-sistema/{id}")
    @PreAuthorize("hasAuthority('USUARIOS_GESTIONAR')")
    @Operation(summary = "Desactiva un rol del sistema (sus usuarios dejan de recibir sus permisos)")
    public ResponseEntity<Void> desactivar(@PathVariable UUID id) {
        servicio.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/permisos")
    @PreAuthorize("hasAuthority('USUARIOS_VER')")
    @Operation(summary = "Permisos existentes")
    public List<PermisoResponse> permisos() {
        return servicio.permisos();
    }
}
