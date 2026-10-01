package com.hackaton.ulibre.protocolos;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemPlantillaChecklistRepository extends JpaRepository<ItemPlantillaChecklist, UUID> {

    List<ItemPlantillaChecklist> findAllByFaseIdInOrderByOrdenAscCodigoAsc(Collection<UUID> faseIds);

    List<ItemPlantillaChecklist> findAllByFaseIdOrderByOrdenAscCodigoAsc(UUID faseId);

    Optional<ItemPlantillaChecklist> findByIdAndFaseId(UUID id, UUID faseId);
}
