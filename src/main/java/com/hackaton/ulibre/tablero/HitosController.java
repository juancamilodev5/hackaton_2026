package com.hackaton.ulibre.tablero;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cirugias/{cirugiaId}/hitos")
@Tag(name = "Hitos")
public class HitosController {

    private final HitosService servicio;

    public HitosController(HitosService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TABLERO_VER')")
    @Operation(summary = "Hitos en orden cronológico (por defecto solo vigentes; incluirAnulados=true trae la historia)")
    public List<HitoDetalle> listar(@PathVariable UUID cirugiaId,
            @RequestParam(defaultValue = "false") boolean incluirAnulados) {
        return servicio.listar(cirugiaId, incluirAnulados);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TABLERO_OPERAR')")
    @Operation(summary = "Registra un hito (solo el operador del tablero); uno vigente por tipo")
    public ResponseEntity<HitoDetalle> registrar(@PathVariable UUID cirugiaId,
            @Valid @RequestBody NuevoHitoRequest datos) {
        HitoDetalle creado = servicio.registrar(cirugiaId, datos);
        return ResponseEntity.created(URI.create("/api/cirugias/" + cirugiaId + "/hitos/" + creado.id()))
                .body(creado);
    }

    @PostMapping("/{hitoId}/anular")
    @PreAuthorize("hasAuthority('TABLERO_OPERAR')")
    @Operation(summary = "Anula un hito (no se borra ni se edita); luego puede registrarse su corrección con corrigeHitoId")
    public HitoDetalle anular(@PathVariable UUID cirugiaId, @PathVariable UUID hitoId,
            @Valid @RequestBody AnularHitoRequest datos) {
        return servicio.anular(cirugiaId, hitoId, datos);
    }
}
