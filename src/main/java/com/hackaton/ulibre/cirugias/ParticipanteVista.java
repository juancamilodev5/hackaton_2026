package com.hackaton.ulibre.cirugias;

import java.util.UUID;

import com.hackaton.ulibre.catalogos.RolClinicoVista;

/** Quién actuó en la cirugía y en qué calidad (el rol del requerimiento que cubre su asignación). */
public record ParticipanteVista(UUID asignacionId, String nombre, RolClinicoVista rol) {
}
