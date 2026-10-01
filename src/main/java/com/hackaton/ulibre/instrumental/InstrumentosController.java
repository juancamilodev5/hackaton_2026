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
@RequestMapping("/api/instrumentos")
@Tag(name = "Instrumental")
public class InstrumentosController {

    private final InstrumentoQuirurgicoService servicio;

    public InstrumentosController(InstrumentoQuirurgicoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INSTRUMENTAL_VER')")
    @Operation(summary = "Instrumentos quirúrgicos, ordenados por nombre (filtro opcional por activo)")
    public List<CatalogoResponse> listar(@RequestParam(required = false) Boolean activo) {
        return servicio.listar(activo);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INSTRUMENTAL_VER')")
    @Operation(summary = "Instrumento por id")
    public CatalogoResponse obtener(@PathVariable UUID id) {
        return servicio.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INSTRUMENTAL_GESTIONAR')")
    @Operation(summary = "Crea un instrumento (código en mayúsculas, único)")
    public ResponseEntity<CatalogoResponse> crear(@Valid @RequestBody CatalogoRequest datos) {
        CatalogoResponse creado = servicio.crear(datos);
        return ResponseEntity.created(URI.create("/api/instrumentos/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('INSTRUMENTAL_GESTIONAR')")
    @Operation(summary = "Actualiza un instrumento")
    public CatalogoResponse actualizar(@PathVariable UUID id, @Valid @RequestBody CatalogoRequest datos) {
        return servicio.actualizar(id, datos);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('INSTRUMENTAL_GESTIONAR')")
    @Operation(summary = "Desactiva un instrumento (no se borra: puede tener histórico)")
    public ResponseEntity<Void> desactivar(@PathVariable UUID id) {
        servicio.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
