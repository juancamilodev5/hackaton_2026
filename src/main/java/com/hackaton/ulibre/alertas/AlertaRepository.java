package com.hackaton.ulibre.alertas;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertaRepository extends JpaRepository<Alerta, UUID> {

    Optional<Alerta> findByIdAndCirugiaId(UUID id, UUID cirugiaId);
}
