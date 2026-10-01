package com.hackaton.ulibre.alertas;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReglaSeguridadRepository extends JpaRepository<ReglaSeguridad, UUID> {

    List<ReglaSeguridad> findAllByOrderByCodigo();

    List<ReglaSeguridad> findAllByActivoOrderByCodigo(boolean activo);
}
