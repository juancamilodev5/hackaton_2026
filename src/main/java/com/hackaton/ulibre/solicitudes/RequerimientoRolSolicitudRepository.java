package com.hackaton.ulibre.solicitudes;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequerimientoRolSolicitudRepository extends JpaRepository<RequerimientoRolSolicitud, UUID> {

    List<RequerimientoRolSolicitud> findAllBySolicitudCirugiaIdOrderByCreadoEnAscIdAsc(UUID solicitudCirugiaId);
}
