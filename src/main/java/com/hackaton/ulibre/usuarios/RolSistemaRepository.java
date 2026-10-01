package com.hackaton.ulibre.usuarios;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RolSistemaRepository extends JpaRepository<RolSistema, UUID> {
}
