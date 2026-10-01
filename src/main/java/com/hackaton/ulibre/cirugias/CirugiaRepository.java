package com.hackaton.ulibre.cirugias;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CirugiaRepository extends JpaRepository<Cirugia, UUID> {
}
