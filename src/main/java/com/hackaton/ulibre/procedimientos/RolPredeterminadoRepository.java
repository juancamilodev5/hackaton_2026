package com.hackaton.ulibre.procedimientos;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RolPredeterminadoRepository extends JpaRepository<RolPredeterminadoProcedimiento, UUID> {

    Optional<RolPredeterminadoProcedimiento> findByIdAndProcedimientoId(UUID id, UUID procedimientoId);
}
