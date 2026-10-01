package com.hackaton.ulibre.tablero;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecuentoCirugiaRepository extends JpaRepository<RecuentoCirugia, UUID> {
}
