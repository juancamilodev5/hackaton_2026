package com.hackaton.ulibre.solicitudes;

import java.util.List;
import java.util.UUID;

import com.hackaton.ulibre.cirugias.Lateralidad;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta de una solicitud. medicoSolicitanteId vacío → el perfil profesional del usuario autenticado.
 * requerimientos ausente (null) → se copian los roles predeterminados del procedimiento.
 */
public record SolicitudCirugiaRequest(
        @NotNull UUID pacienteId,
        UUID medicoSolicitanteId,
        UUID citaOrigenId,
        @NotNull UUID especialidadSolicitanteId,
        @NotNull UUID procedimientoId,
        @Size(max = 150) String sitioQuirurgico,
        Lateralidad lateralidad,
        @Size(max = 10000) String resumenClinico,
        @Size(max = 10000) String notasMedicas,
        List<@Valid RequerimientoRequest> requerimientos) {
}
