package com.hackaton.ulibre.tablero;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cirugias/{cirugiaId}/tablero")
@Tag(name = "Tablero de seguridad")
public class TableroController {

    private final TableroService tableroService;

    public TableroController(TableroService tableroService) {
        this.tableroService = tableroService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TABLERO_VER')")
    @Operation(summary = "Estado completo del tablero de una cirugía")
    public TableroVista tablero(@PathVariable UUID cirugiaId) {
        return tableroService.tablero(cirugiaId);
    }
}
