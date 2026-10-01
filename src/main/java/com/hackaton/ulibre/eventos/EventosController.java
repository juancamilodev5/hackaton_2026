package com.hackaton.ulibre.eventos;

import java.util.List;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.ParticipacionCirugia;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cirugias/{cirugiaId}/eventos")
@Tag(name = "Trazabilidad")
public class EventosController {

    private final EventosCirugia eventos;
    private final ParticipacionCirugia participacion;

    public EventosController(EventosCirugia eventos, ParticipacionCirugia participacion) {
        this.eventos = eventos;
        this.participacion = participacion;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('TABLERO_VER', 'AUDITORIA_VER')")
    @Transactional(readOnly = true)
    @Operation(summary = "Timeline de eventos de la cirugía: qué, quién (y en qué rol), cuándo")
    public List<EventoVista> listar(@PathVariable UUID cirugiaId) {
        participacion.exigirCirugia(cirugiaId);
        return eventos.deCirugia(cirugiaId);
    }
}
