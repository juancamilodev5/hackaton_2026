package com.hackaton.ulibre.checklist;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FaseChecklistCirugiaRepository extends JpaRepository<FaseChecklistCirugia, UUID> {

    List<FaseChecklistCirugia> findAllByChecklistCirugiaIdOrderByOrden(UUID checklistCirugiaId);
}
