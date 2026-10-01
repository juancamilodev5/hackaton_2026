package com.hackaton.ulibre.profesionales;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PerfilProfesionalRepository extends JpaRepository<PerfilProfesional, UUID> {
}
