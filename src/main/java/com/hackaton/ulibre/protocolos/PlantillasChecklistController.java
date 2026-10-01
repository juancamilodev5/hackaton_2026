package com.hackaton.ulibre.protocolos;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plantillas-checklist")
@Tag(name = "Protocolos de checklist")
public class PlantillasChecklistController {

    private static final String RUTA = "/api/plantillas-checklist/";

    private final PlantillaChecklistService servicio;

    public PlantillasChecklistController(PlantillaChecklistService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROTOCOLOS_VER')")
    @Operation(summary = "Plantillas (filtros opcionales por código y estado), la versión más nueva primero")
    public List<PlantillaResponse> listar(@RequestParam(required = false) String codigo,
            @RequestParam(required = false) EstadoPlantilla estado) {
        return servicio.listar(codigo, estado);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PROTOCOLOS_VER')")
    @Operation(summary = "Plantilla completa: fases ordenadas con sus ítems")
    public PlantillaDetalleResponse obtener(@PathVariable UUID id) {
        return servicio.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROTOCOLOS_GESTIONAR')")
    @Operation(summary = "Crea una plantilla en BORRADOR (versión = la mayor del código + 1)")
    public ResponseEntity<PlantillaDetalleResponse> crear(@Valid @RequestBody PlantillaRequest datos) {
        PlantillaDetalleResponse creada = servicio.crear(datos);
        return ResponseEntity.created(URI.create(RUTA + creada.id())).body(creada);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PROTOCOLOS_GESTIONAR')")
    @Operation(summary = "Edita una plantilla en BORRADOR (publicada o retirada → 422)")
    public PlantillaDetalleResponse actualizar(@PathVariable UUID id, @Valid @RequestBody PlantillaRequest datos) {
        return servicio.actualizar(id, datos);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PROTOCOLOS_GESTIONAR')")
    @Operation(summary = "Borra una plantilla en BORRADOR con sus fases e ítems")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publicar")
    @PreAuthorize("hasAuthority('PROTOCOLOS_GESTIONAR')")
    @Operation(summary = "Publica un BORRADOR (debe tener fases y cada fase al menos un ítem); queda inmutable")
    public PlantillaDetalleResponse publicar(@PathVariable UUID id) {
        return servicio.publicar(id);
    }

    @PostMapping("/{id}/retirar")
    @PreAuthorize("hasAuthority('PROTOCOLOS_GESTIONAR')")
    @Operation(summary = "Retira una plantilla PUBLICADA (no se usa en checklists nuevos)")
    public PlantillaDetalleResponse retirar(@PathVariable UUID id) {
        return servicio.retirar(id);
    }

    @PostMapping("/{id}/nueva-version")
    @PreAuthorize("hasAuthority('PROTOCOLOS_GESTIONAR')")
    @Operation(summary = "Copia la plantilla (fases e ítems) a un BORRADOR con la siguiente versión")
    public ResponseEntity<PlantillaDetalleResponse> nuevaVersion(@PathVariable UUID id) {
        PlantillaDetalleResponse creada = servicio.nuevaVersion(id);
        return ResponseEntity.created(URI.create(RUTA + creada.id())).body(creada);
    }

    @PostMapping("/{id}/fases")
    @PreAuthorize("hasAuthority('PROTOCOLOS_GESTIONAR')")
    @Operation(summary = "Agrega una fase a una plantilla en BORRADOR")
    public ResponseEntity<FasePlantillaResponse> crearFase(@PathVariable UUID id,
            @Valid @RequestBody FasePlantillaRequest datos) {
        FasePlantillaResponse creada = servicio.crearFase(id, datos);
        return ResponseEntity.created(URI.create(RUTA + id + "/fases/" + creada.id())).body(creada);
    }

    @PutMapping("/{id}/fases/{faseId}")
    @PreAuthorize("hasAuthority('PROTOCOLOS_GESTIONAR')")
    @Operation(summary = "Edita una fase de una plantilla en BORRADOR")
    public FasePlantillaResponse actualizarFase(@PathVariable UUID id, @PathVariable UUID faseId,
            @Valid @RequestBody FasePlantillaRequest datos) {
        return servicio.actualizarFase(id, faseId, datos);
    }

    @DeleteMapping("/{id}/fases/{faseId}")
    @PreAuthorize("hasAuthority('PROTOCOLOS_GESTIONAR')")
    @Operation(summary = "Borra una fase y sus ítems de una plantilla en BORRADOR")
    public ResponseEntity<Void> eliminarFase(@PathVariable UUID id, @PathVariable UUID faseId) {
        servicio.eliminarFase(id, faseId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/fases/{faseId}/items")
    @PreAuthorize("hasAuthority('PROTOCOLOS_GESTIONAR')")
    @Operation(summary = "Agrega un ítem a una fase de una plantilla en BORRADOR")
    public ResponseEntity<ItemPlantillaResponse> crearItem(@PathVariable UUID id, @PathVariable UUID faseId,
            @Valid @RequestBody ItemPlantillaRequest datos) {
        ItemPlantillaResponse creado = servicio.crearItem(id, faseId, datos);
        return ResponseEntity.created(URI.create(RUTA + id + "/fases/" + faseId + "/items/" + creado.id()))
                .body(creado);
    }

    @PutMapping("/{id}/fases/{faseId}/items/{itemId}")
    @PreAuthorize("hasAuthority('PROTOCOLOS_GESTIONAR')")
    @Operation(summary = "Edita un ítem de una plantilla en BORRADOR")
    public ItemPlantillaResponse actualizarItem(@PathVariable UUID id, @PathVariable UUID faseId,
            @PathVariable UUID itemId, @Valid @RequestBody ItemPlantillaRequest datos) {
        return servicio.actualizarItem(id, faseId, itemId, datos);
    }

    @DeleteMapping("/{id}/fases/{faseId}/items/{itemId}")
    @PreAuthorize("hasAuthority('PROTOCOLOS_GESTIONAR')")
    @Operation(summary = "Borra un ítem de una plantilla en BORRADOR")
    public ResponseEntity<Void> eliminarItem(@PathVariable UUID id, @PathVariable UUID faseId,
            @PathVariable UUID itemId) {
        servicio.eliminarItem(id, faseId, itemId);
        return ResponseEntity.noContent().build();
    }
}
