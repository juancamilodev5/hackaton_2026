package com.hackaton.ulibre.protocolos;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlantillaChecklistRepository extends JpaRepository<PlantillaChecklist, UUID> {

    /** 0 si el código aún no existe. */
    @Query("SELECT coalesce(max(p.version), 0) FROM PlantillaChecklist p WHERE p.codigo = :codigo")
    int maximaVersion(String codigo);
}
