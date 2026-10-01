package com.hackaton.ulibre.pacientes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlergiaPacienteRepository extends JpaRepository<AlergiaPaciente, UUID> {

    List<AlergiaPaciente> findAllByPacienteIdOrderBySustancia(UUID pacienteId);

    List<AlergiaPaciente> findAllByPacienteIdAndActivoOrderBySustancia(UUID pacienteId, boolean activo);

    Optional<AlergiaPaciente> findByIdAndPacienteId(UUID id, UUID pacienteId);

    void deleteAllByPacienteId(UUID pacienteId);
}
