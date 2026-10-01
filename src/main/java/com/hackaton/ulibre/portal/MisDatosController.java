package com.hackaton.ulibre.portal;

import java.util.List;
import java.util.UUID;

import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal del paciente: "el paciente solo ve lo suyo". La semilla V2 deja al rol PACIENTE sin
 * permisos globales y dice que lo propio se filtra en backend; por eso basta estar autenticado y la
 * pertenencia se impone en cada consulta (pacientes.usuario_id = usuario del token).
 */
@RestController
@RequestMapping("/api/mi")
@Tag(name = "Mis datos")
@PreAuthorize("isAuthenticated()")
public class MisDatosController {

    private final MisDatosConsultas consultas;

    public MisDatosController(MisDatosConsultas consultas) {
        this.consultas = consultas;
    }

    @GetMapping("/paciente")
    @Transactional(readOnly = true)
    @Operation(summary = "Mis datos de paciente, edad calculada y alergias activas")
    public MiPacienteResponse paciente() {
        return consultas.paciente(consultas.pacienteActual());
    }

    @GetMapping("/citas")
    @Transactional(readOnly = true)
    @Operation(summary = "Mis citas, más recientes primero")
    public List<MiCitaResponse> citas() {
        return consultas.citas(consultas.pacienteActual());
    }

    @GetMapping("/solicitudes")
    @Transactional(readOnly = true)
    @Operation(summary = "Mis solicitudes de cirugía (sin notas médicas internas)")
    public List<MiSolicitudResponse> solicitudes() {
        return consultas.solicitudes(consultas.pacienteActual());
    }

    @GetMapping("/cirugias")
    @Transactional(readOnly = true)
    @Operation(summary = "Mis cirugías programadas")
    public List<MiCirugiaResponse> cirugias() {
        return consultas.cirugias(consultas.pacienteActual());
    }

    @GetMapping("/cirugias/{id}")
    @Transactional(readOnly = true)
    @Operation(summary = "Una de mis cirugías (404 si no existe o no es mía)")
    public MiCirugiaResponse cirugia(@PathVariable UUID id) {
        return consultas.cirugia(consultas.pacienteActual(), id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cirugía no encontrada: " + id));
    }
}
