package com.hackaton.ulibre.cirugias;

import java.time.LocalDateTime;
import java.util.UUID;

import com.hackaton.ulibre.catalogos.QuirofanoVista;

public record CirugiaVista(
        UUID id,
        EstadoCirugia estado,
        QuirofanoVista quirofano,
        LocalDateTime inicioProgramado,
        LocalDateTime finProgramado) {
}
