package com.hackaton.ulibre.auditoria;

import static com.hackaton.ulibre.comun.Filas.fechaHora;
import static com.hackaton.ulibre.comun.Filas.uuid;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.hackaton.ulibre.comun.Pagina;
import com.hackaton.ulibre.comun.Paginacion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auditoria")
@Tag(name = "Trazabilidad")
public class AuditoriaController {

    private final JdbcClient jdbc;

    public AuditoriaController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDITORIA_VER')")
    @Transactional(readOnly = true)
    @Operation(summary = "Registros de auditoría administrativa, más recientes primero (desde/hasta inclusive)")
    public Pagina<RegistroAuditoriaVista> listar(
            @RequestParam(required = false) String tipoEntidad,
            @RequestParam(required = false) UUID entidadId,
            @RequestParam(required = false) UUID usuarioId,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamano) {
        Paginacion paginacion = Paginacion.de(pagina, tamano);
        StringBuilder where = new StringBuilder(" WHERE true");
        Map<String, Object> params = new HashMap<>();
        if (tipoEntidad != null) {
            where.append(" AND r.tipo_entidad = :tipoEntidad");
            params.put("tipoEntidad", tipoEntidad);
        }
        if (entidadId != null) {
            where.append(" AND r.entidad_id = :entidadId");
            params.put("entidadId", entidadId);
        }
        if (usuarioId != null) {
            where.append(" AND r.usuario_id = :usuarioId");
            params.put("usuarioId", usuarioId);
        }
        if (accion != null) {
            where.append(" AND r.accion = :accion");
            params.put("accion", accion);
        }
        if (desde != null) {
            where.append(" AND r.ocurrido_en >= :desde");
            params.put("desde", desde.atStartOfDay());
        }
        if (hasta != null) {
            where.append(" AND r.ocurrido_en < :hasta");
            params.put("hasta", hasta.plusDays(1).atStartOfDay());
        }
        long total = jdbc.sql("SELECT count(*) FROM registros_auditoria r" + where)
                .params(params)
                .query(Long.class)
                .single();
        List<RegistroAuditoriaVista> contenido = jdbc.sql("""
                        SELECT r.id, r.ocurrido_en, r.usuario_id, u.nombres || ' ' || u.apellidos AS usuario,
                               r.tipo_entidad, r.entidad_id, r.accion,
                               r.valores_anteriores::text AS anteriores, r.valores_nuevos::text AS nuevos
                        FROM registros_auditoria r
                                 LEFT JOIN usuarios u ON u.id = r.usuario_id
                        """ + where + " ORDER BY r.ocurrido_en DESC, r.id DESC LIMIT :limite OFFSET :desplazamiento")
                .params(params)
                .param("limite", paginacion.tamano())
                .param("desplazamiento", paginacion.desplazamiento())
                .query((rs, n) -> new RegistroAuditoriaVista(
                        uuid(rs, "id"),
                        fechaHora(rs, "ocurrido_en"),
                        uuid(rs, "usuario_id"),
                        rs.getString("usuario"),
                        rs.getString("tipo_entidad"),
                        uuid(rs, "entidad_id"),
                        rs.getString("accion"),
                        rs.getString("anteriores"),
                        rs.getString("nuevos")))
                .list();
        return Pagina.de(contenido, paginacion, total);
    }
}
