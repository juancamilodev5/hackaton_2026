package com.hackaton.ulibre.pacientes;

import java.util.List;
import java.util.UUID;

import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Alergias de un paciente. "Eliminar" desactiva: las copias en cirugías ya hechas no cambian. */
@Service
public class AlergiaPacienteService {

    private final AlergiaPacienteRepository alergias;
    private final PacienteService pacientes;

    public AlergiaPacienteService(AlergiaPacienteRepository alergias, PacienteService pacientes) {
        this.alergias = alergias;
        this.pacientes = pacientes;
    }

    @Transactional(readOnly = true)
    public List<AlergiaResponse> listar(UUID pacienteId, Boolean activo) {
        pacientes.buscar(pacienteId);
        List<AlergiaPaciente> filas = activo == null ? alergias.findAllByPacienteIdOrderBySustancia(pacienteId)
                : alergias.findAllByPacienteIdAndActivoOrderBySustancia(pacienteId, activo);
        return filas.stream().map(AlergiaResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public AlergiaResponse obtener(UUID pacienteId, UUID alergiaId) {
        return AlergiaResponse.de(buscar(pacienteId, alergiaId));
    }

    @Transactional
    public AlergiaResponse crear(UUID pacienteId, AlergiaRequest datos) {
        pacientes.buscar(pacienteId);
        AlergiaPaciente alergia = new AlergiaPaciente();
        alergia.setPacienteId(pacienteId);
        alergia.setActivo(true);
        aplicar(alergia, datos);
        return AlergiaResponse.de(alergias.saveAndFlush(alergia));
    }

    @Transactional
    public AlergiaResponse actualizar(UUID pacienteId, UUID alergiaId, AlergiaRequest datos) {
        AlergiaPaciente alergia = buscar(pacienteId, alergiaId);
        aplicar(alergia, datos);
        return AlergiaResponse.de(alergias.saveAndFlush(alergia));
    }

    @Transactional
    public void desactivar(UUID pacienteId, UUID alergiaId) {
        AlergiaPaciente alergia = buscar(pacienteId, alergiaId);
        alergia.setActivo(false);
        alergias.saveAndFlush(alergia);
    }

    /** 404 también si la alergia existe pero es de otro paciente. */
    private AlergiaPaciente buscar(UUID pacienteId, UUID alergiaId) {
        pacientes.buscar(pacienteId);
        return alergias.findByIdAndPacienteId(alergiaId, pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Alergia no encontrada para el paciente: " + alergiaId));
    }

    private static void aplicar(AlergiaPaciente alergia, AlergiaRequest datos) {
        alergia.setSustancia(Textos.limpiar(datos.sustancia()));
        alergia.setReaccion(Textos.limpiar(datos.reaccion()));
        alergia.setSeveridad(Textos.codigo(datos.severidad()));
        alergia.setNotas(Textos.limpiar(datos.notas()));
        if (datos.activo() != null) {
            alergia.setActivo(datos.activo());
        }
    }
}
