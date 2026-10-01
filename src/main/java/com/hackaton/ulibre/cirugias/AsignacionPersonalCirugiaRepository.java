package com.hackaton.ulibre.cirugias;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AsignacionPersonalCirugiaRepository extends JpaRepository<AsignacionPersonalCirugia, UUID> {

    Optional<AsignacionPersonalCirugia> findByIdAndCirugiaId(UUID id, UUID cirugiaId);

    Optional<AsignacionPersonalCirugia> findByCirugiaIdAndProfesionalId(UUID cirugiaId, UUID profesionalId);

    Optional<AsignacionPersonalCirugia> findByCirugiaIdAndEsOperadorTableroTrueAndEstadoIn(UUID cirugiaId,
            Collection<EstadoAsignacion> estados);
}
