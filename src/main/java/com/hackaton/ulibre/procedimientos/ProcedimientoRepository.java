package com.hackaton.ulibre.procedimientos;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcedimientoRepository extends JpaRepository<ProcedimientoQuirurgico, UUID> {

    /** Filtros opcionales: null = sin filtrar. La especialidad se resuelve con procedimiento_especialidades. */
    @Query(value = """
            SELECT p.* FROM procedimientos_quirurgicos p
            WHERE (CAST(:activo AS boolean) IS NULL OR p.activo = CAST(:activo AS boolean))
              AND (CAST(:especialidadId AS uuid) IS NULL OR EXISTS (
                      SELECT 1 FROM procedimiento_especialidades pe
                      WHERE pe.procedimiento_id = p.id AND pe.especialidad_id = CAST(:especialidadId AS uuid)))
            ORDER BY p.nombre
            """, nativeQuery = true)
    List<ProcedimientoQuirurgico> buscar(@Param("activo") Boolean activo, @Param("especialidadId") UUID especialidadId);
}
