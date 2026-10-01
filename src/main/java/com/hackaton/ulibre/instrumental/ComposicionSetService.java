package com.hackaton.ulibre.instrumental;

import static com.hackaton.ulibre.comun.Filas.uuid;

import java.util.List;
import java.util.UUID;

import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composición de un set (tabla instrumentos_set, PK compuesta) con SQL nativo. cantidad > 0 la
 * garantiza ck_instrumentos_set_cantidad. Cambiar la composición no altera cirugías ya preparadas:
 * fn_preparar_instrumental copia un snapshot.
 */
@Service
public class ComposicionSetService {

    private final JdbcClient jdbc;
    private final SetInstrumentalService sets;
    private final InstrumentoQuirurgicoService instrumentos;

    public ComposicionSetService(JdbcClient jdbc, SetInstrumentalService sets,
            InstrumentoQuirurgicoService instrumentos) {
        this.jdbc = jdbc;
        this.sets = sets;
        this.instrumentos = instrumentos;
    }

    @Transactional(readOnly = true)
    public SetInstrumentalDetalleResponse detalle(UUID setId) {
        return SetInstrumentalDetalleResponse.de(sets.obtener(setId), filas(setId));
    }

    @Transactional(readOnly = true)
    public List<InstrumentoSetResponse> listar(UUID setId) {
        sets.buscar(setId);
        return filas(setId);
    }

    @Transactional
    public List<InstrumentoSetResponse> fijarCantidad(UUID setId, UUID instrumentoId, int cantidad) {
        sets.buscar(setId);
        instrumentos.buscar(instrumentoId);
        jdbc.sql("""
                        INSERT INTO instrumentos_set (set_instrumental_id, instrumento_id, cantidad)
                        VALUES (:setId, :instrumentoId, :cantidad)
                        ON CONFLICT (set_instrumental_id, instrumento_id) DO UPDATE SET cantidad = EXCLUDED.cantidad
                        """)
                .param("setId", setId)
                .param("instrumentoId", instrumentoId)
                .param("cantidad", cantidad)
                .update();
        return filas(setId);
    }

    @Transactional
    public void quitar(UUID setId, UUID instrumentoId) {
        int borradas = jdbc.sql("""
                        DELETE FROM instrumentos_set
                        WHERE set_instrumental_id = :setId AND instrumento_id = :instrumentoId
                        """)
                .param("setId", setId)
                .param("instrumentoId", instrumentoId)
                .update();
        if (borradas == 0) {
            throw new RecursoNoEncontradoException("El instrumento " + instrumentoId + " no forma parte del set " + setId);
        }
    }

    private List<InstrumentoSetResponse> filas(UUID setId) {
        return jdbc.sql("""
                        SELECT i.id, i.codigo, i.nombre, i.activo, s.cantidad
                        FROM instrumentos_set s
                                 JOIN instrumentos_quirurgicos i ON i.id = s.instrumento_id
                        WHERE s.set_instrumental_id = :setId
                        ORDER BY i.nombre, i.id
                        """)
                .param("setId", setId)
                .query((rs, n) -> new InstrumentoSetResponse(uuid(rs, "id"), rs.getString("codigo"),
                        rs.getString("nombre"), rs.getBoolean("activo"), rs.getInt("cantidad")))
                .list();
    }
}
