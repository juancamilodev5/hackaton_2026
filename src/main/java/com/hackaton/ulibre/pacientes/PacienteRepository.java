package com.hackaton.ulibre.pacientes;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PacienteRepository extends JpaRepository<Paciente, UUID> {

    /** {@code patron} ya viene en minúsculas, escapado y con comodines (Textos.patronLike). */
    @Query("""
            SELECT p FROM Paciente p
            WHERE lower(p.nombres) LIKE :patron
               OR lower(p.apellidos) LIKE :patron
               OR lower(concat(p.nombres, ' ', p.apellidos)) LIKE :patron
               OR lower(p.numeroDocumento) LIKE :patron
            """)
    Page<Paciente> buscar(@Param("patron") String patron, Pageable pageable);
}
