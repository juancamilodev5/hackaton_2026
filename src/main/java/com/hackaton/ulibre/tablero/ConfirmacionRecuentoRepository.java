package com.hackaton.ulibre.tablero;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfirmacionRecuentoRepository extends JpaRepository<ConfirmacionRecuento, UUID> {

    boolean existsByRecuentoCirugiaIdAndEtapa(UUID recuentoCirugiaId, EtapaRecuento etapa);
}
