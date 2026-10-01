package com.hackaton.ulibre.profesionales;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DisponibilidadProfesionalRepository extends JpaRepository<DisponibilidadProfesional, UUID> {

    /** Bloques que se solapan con [desde, hasta). */
    List<DisponibilidadProfesional> findAllByProfesionalIdAndTerminaEnAfterAndIniciaEnBeforeOrderByIniciaEn(
            UUID profesionalId, LocalDateTime desde, LocalDateTime hasta);

    Optional<DisponibilidadProfesional> findByIdAndProfesionalId(UUID id, UUID profesionalId);
}
