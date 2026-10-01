package com.hackaton.ulibre.protocolos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FasePlantillaChecklistRepository extends JpaRepository<FasePlantillaChecklist, UUID> {

    List<FasePlantillaChecklist> findAllByPlantillaIdOrderByOrdenAscCodigoAsc(UUID plantillaId);

    Optional<FasePlantillaChecklist> findByIdAndPlantillaId(UUID id, UUID plantillaId);
}
