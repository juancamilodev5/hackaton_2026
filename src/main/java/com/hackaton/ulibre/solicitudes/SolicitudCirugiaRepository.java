package com.hackaton.ulibre.solicitudes;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudCirugiaRepository extends JpaRepository<SolicitudCirugia, UUID> {
}
