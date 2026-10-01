package com.hackaton.ulibre.instrumental;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.hackaton.ulibre.comun.catalogo.CatalogoRequest;
import com.hackaton.ulibre.comun.catalogo.CatalogoResponse;
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
@RequestMapping("/api/sets-instrumentales")
@Tag(name = "Instrumental")
public class SetsInstrumentalesController {

    private final SetInstrumentalService servicio;
    private final ComposicionSetService composicion;

    public SetsInstrumentalesController(SetInstrumentalService servicio, ComposicionSetService composicion) {
        this.servicio = servicio;
        this.composicion = composicion;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INSTRUMENTAL_VER')")
    @Operation(summary = "Sets instrumentales, ordenados por nombre (filtro opcional por activo)")
    public List<CatalogoResponse> listar(@RequestParam(required = false) Boolean activo) {
        return servicio.listar(activo);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INSTRUMENTAL_VER')")
    @Operation(summary = "Set instrumental por id, con su composición")
    public SetInstrumentalDetalleResponse obtener(@PathVariable UUID id) {
        return composicion.detalle(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INSTRUMENTAL_GESTIONAR')")
    @Operation(summary = "Crea un set instrumental (código en mayúsculas, único)")
    public ResponseEntity<CatalogoResponse> crear(@Valid @RequestBody CatalogoRequest datos) {
        CatalogoResponse creado = servicio.crear(datos);
        return ResponseEntity.created(URI.create("/api/sets-instrumentales/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('INSTRUMENTAL_GESTIONAR')")
    @Operation(summary = "Actualiza un set instrumental")
    public CatalogoResponse actualizar(@PathVariable UUID id, @Valid @RequestBody CatalogoRequest datos) {
        return servicio.actualizar(id, datos);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('INSTRUMENTAL_GESTIONAR')")
    @Operation(summary = "Desactiva un set instrumental (no se borra: puede tener histórico)")
    public ResponseEntity<Void> desactivar(@PathVariable UUID id) {
        servicio.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/instrumentos")
    @PreAuthorize("hasAuthority('INSTRUMENTAL_VER')")
    @Operation(summary = "Composición del set: instrumentos y cantidades")
    public List<InstrumentoSetResponse> instrumentos(@PathVariable UUID id) {
        return composicion.listar(id);
    }

    @PutMapping("/{id}/instrumentos/{instrumentoId}")
    @PreAuthorize("hasAuthority('INSTRUMENTAL_GESTIONAR')")
    @Operation(summary = "Agrega un instrumento al set o cambia su cantidad; devuelve la composición")
    public List<InstrumentoSetResponse> fijarInstrumento(@PathVariable UUID id, @PathVariable UUID instrumentoId,
            @Valid @RequestBody CantidadInstrumentoRequest datos) {
        return composicion.fijarCantidad(id, instrumentoId, datos.cantidad());
    }

    @DeleteMapping("/{id}/instrumentos/{instrumentoId}")
    @PreAuthorize("hasAuthority('INSTRUMENTAL_GESTIONAR')")
    @Operation(summary = "Quita un instrumento del set")
    public ResponseEntity<Void> quitarInstrumento(@PathVariable UUID id, @PathVariable UUID instrumentoId) {
        composicion.quitar(id, instrumentoId);
        return ResponseEntity.noContent().build();
    }
}
