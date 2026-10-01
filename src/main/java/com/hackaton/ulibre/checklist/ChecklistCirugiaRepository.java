package com.hackaton.ulibre.checklist;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistCirugiaRepository extends JpaRepository<ChecklistCirugia, UUID> {

    /** El más reciente, igual que la lectura del tablero (fn_iniciar_checklist impide más de uno). */
    Optional<ChecklistCirugia> findFirstByCirugiaIdOrderByCreadoEnDesc(UUID cirugiaId);
}
