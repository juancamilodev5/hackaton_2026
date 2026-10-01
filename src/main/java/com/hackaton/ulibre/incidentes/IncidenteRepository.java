package com.hackaton.ulibre.incidentes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidenteRepository extends JpaRepository<Incidente, UUID> {

    List<Incidente> findAllByCirugiaIdOrderByReportadoEnDesc(UUID cirugiaId);

    Optional<Incidente> findByIdAndCirugiaId(UUID id, UUID cirugiaId);
}
