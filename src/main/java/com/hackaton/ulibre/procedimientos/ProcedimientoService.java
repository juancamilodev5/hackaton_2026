package com.hackaton.ulibre.procedimientos;

import static com.hackaton.ulibre.comun.Filas.uuid;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.hackaton.ulibre.auditoria.AccionAuditoria;
import com.hackaton.ulibre.auditoria.Auditoria;
import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.Textos;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Procedimientos y su configuración. "Eliminar" un procedimiento lo desactiva (regla 13); las tablas
 * puente (especialidades, plantillas, sets e instrumentos predeterminados) sí se borran físicamente:
 * si una solicitud usa la relación procedimiento-especialidad, la FK lo impide (422). Cada cambio queda
 * en registros_auditoria (las tablas puente, con el procedimiento como entidad).
 */
@Service
public class ProcedimientoService {

    private static final String TABLA = "procedimientos_quirurgicos";

    private final ProcedimientoRepository procedimientos;
    private final RolPredeterminadoRepository rolesPredeterminados;
    private final JdbcClient jdbc;
    private final Auditoria auditoria;

    public ProcedimientoService(ProcedimientoRepository procedimientos,
            RolPredeterminadoRepository rolesPredeterminados, JdbcClient jdbc, Auditoria auditoria) {
        this.procedimientos = procedimientos;
        this.rolesPredeterminados = rolesPredeterminados;
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    // ------------------------------------------------------------------ procedimiento

    @Transactional(readOnly = true)
    public List<ProcedimientoResponse> listar(Boolean activo, UUID especialidadId) {
        return procedimientos.buscar(activo, especialidadId).stream().map(ProcedimientoResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public ProcedimientoDetalleResponse detalle(UUID id) {
        ProcedimientoResponse procedimiento = ProcedimientoResponse.de(buscar(id));
        return new ProcedimientoDetalleResponse(procedimiento, especialidades(id), rolesPredeterminados(id),
                plantillas(id), setsPredeterminados(id), instrumentosPredeterminados(id));
    }

    @Transactional
    public ProcedimientoResponse crear(ProcedimientoRequest datos) {
        ProcedimientoQuirurgico p = new ProcedimientoQuirurgico();
        p.setActivo(true);
        aplicar(p, datos);
        ProcedimientoResponse creado = ProcedimientoResponse.de(procedimientos.saveAndFlush(p));
        auditoria.registrar(TABLA, creado.id(), AccionAuditoria.CREAR, null, creado);
        return creado;
    }

    @Transactional
    public ProcedimientoResponse actualizar(UUID id, ProcedimientoRequest datos) {
        ProcedimientoQuirurgico p = buscar(id);
        ProcedimientoResponse anterior = ProcedimientoResponse.de(p);
        aplicar(p, datos);
        ProcedimientoResponse actualizado = ProcedimientoResponse.de(procedimientos.saveAndFlush(p));
        auditoria.registrar(TABLA, id, AccionAuditoria.ACTUALIZAR, anterior, actualizado);
        return actualizado;
    }

    @Transactional
    public void desactivar(UUID id) {
        ProcedimientoQuirurgico p = buscar(id);
        ProcedimientoResponse anterior = ProcedimientoResponse.de(p);
        p.setActivo(false);
        auditoria.registrar(TABLA, id, AccionAuditoria.DESACTIVAR, anterior,
                ProcedimientoResponse.de(procedimientos.saveAndFlush(p)));
    }

    // ------------------------------------------------------------------ especialidades

    @Transactional
    public void agregarEspecialidad(UUID id, UUID especialidadId) {
        buscar(id);
        exigir("especialidades", especialidadId, "Especialidad no encontrada");
        jdbc.sql("""
                        INSERT INTO procedimiento_especialidades (procedimiento_id, especialidad_id)
                        VALUES (:procedimientoId, :especialidadId)
                        ON CONFLICT DO NOTHING
                        """)
                .param("procedimientoId", id)
                .param("especialidadId", especialidadId)
                .update();
        auditoria.registrar("procedimiento_especialidades", id, AccionAuditoria.ASOCIAR, null,
                Map.of("especialidadId", especialidadId));
    }

    @Transactional
    public void quitarEspecialidad(UUID id, UUID especialidadId) {
        buscar(id);
        int borradas = jdbc.sql("""
                        DELETE FROM procedimiento_especialidades
                        WHERE procedimiento_id = :procedimientoId AND especialidad_id = :especialidadId
                        """)
                .param("procedimientoId", id)
                .param("especialidadId", especialidadId)
                .update();
        if (borradas == 0) {
            throw new RecursoNoEncontradoException("La especialidad no está habilitada para el procedimiento");
        }
        auditoria.registrar("procedimiento_especialidades", id, AccionAuditoria.DESASOCIAR,
                Map.of("especialidadId", especialidadId), null);
    }

    // ------------------------------------------------------------------ roles predeterminados

    @Transactional(readOnly = true)
    public List<RolPredeterminadoResponse> listarRoles(UUID id) {
        buscar(id);
        return rolesPredeterminados(id);
    }

    @Transactional
    public RolPredeterminadoResponse crearRol(UUID id, RolPredeterminadoRequest datos) {
        buscar(id);
        RolPredeterminadoProcedimiento rol = new RolPredeterminadoProcedimiento();
        rol.setProcedimientoId(id);
        aplicar(rol, datos);
        RolPredeterminadoResponse creado = rolPredeterminado(rolesPredeterminados.saveAndFlush(rol).getId());
        auditoria.registrar("roles_predeterminados_procedimiento", creado.id(), AccionAuditoria.CREAR, null, creado);
        return creado;
    }

    @Transactional
    public RolPredeterminadoResponse actualizarRol(UUID id, UUID rolPredId, RolPredeterminadoRequest datos) {
        RolPredeterminadoProcedimiento rol = buscarRol(id, rolPredId);
        RolPredeterminadoResponse anterior = rolPredeterminado(rolPredId);
        aplicar(rol, datos);
        rolesPredeterminados.saveAndFlush(rol);
        RolPredeterminadoResponse actualizado = rolPredeterminado(rolPredId);
        auditoria.registrar("roles_predeterminados_procedimiento", rolPredId, AccionAuditoria.ACTUALIZAR, anterior,
                actualizado);
        return actualizado;
    }

    @Transactional
    public void borrarRol(UUID id, UUID rolPredId) {
        RolPredeterminadoProcedimiento rol = buscarRol(id, rolPredId);
        RolPredeterminadoResponse anterior = rolPredeterminado(rolPredId);
        rolesPredeterminados.delete(rol);
        rolesPredeterminados.flush();
        auditoria.registrar("roles_predeterminados_procedimiento", rolPredId, AccionAuditoria.ELIMINAR, anterior, null);
    }

    // ------------------------------------------------------------------ plantillas de checklist

    /** Solo una predeterminada por procedimiento (uq_proc_plantilla_predeterminada): se quita la anterior primero. */
    @Transactional
    public void asociarPlantilla(UUID id, UUID plantillaId, PlantillaProcedimientoRequest datos) {
        buscar(id);
        exigir("plantillas_checklist", plantillaId, "Plantilla de checklist no encontrada");
        boolean predeterminada = datos != null && Boolean.TRUE.equals(datos.esPredeterminada());
        if (predeterminada) {
            jdbc.sql("""
                            UPDATE procedimiento_plantillas_checklist SET es_predeterminada = false
                            WHERE procedimiento_id = :procedimientoId AND es_predeterminada
                              AND plantilla_checklist_id <> :plantillaId
                            """)
                    .param("procedimientoId", id)
                    .param("plantillaId", plantillaId)
                    .update();
        }
        jdbc.sql("""
                        INSERT INTO procedimiento_plantillas_checklist (procedimiento_id, plantilla_checklist_id, es_predeterminada)
                        VALUES (:procedimientoId, :plantillaId, :predeterminada)
                        ON CONFLICT (procedimiento_id, plantilla_checklist_id)
                            DO UPDATE SET es_predeterminada = EXCLUDED.es_predeterminada
                        """)
                .param("procedimientoId", id)
                .param("plantillaId", plantillaId)
                .param("predeterminada", predeterminada)
                .update();
        auditoria.registrar("procedimiento_plantillas_checklist", id, AccionAuditoria.ASOCIAR, null,
                Map.of("plantillaId", plantillaId, "esPredeterminada", predeterminada));
    }

    @Transactional
    public void quitarPlantilla(UUID id, UUID plantillaId) {
        buscar(id);
        int borradas = jdbc.sql("""
                        DELETE FROM procedimiento_plantillas_checklist
                        WHERE procedimiento_id = :procedimientoId AND plantilla_checklist_id = :plantillaId
                        """)
                .param("procedimientoId", id)
                .param("plantillaId", plantillaId)
                .update();
        if (borradas == 0) {
            throw new RecursoNoEncontradoException("La plantilla no está asociada al procedimiento");
        }
        auditoria.registrar("procedimiento_plantillas_checklist", id, AccionAuditoria.DESASOCIAR,
                Map.of("plantillaId", plantillaId), null);
    }

    // ------------------------------------------------------------------ instrumental predeterminado

    @Transactional
    public void ponerSet(UUID id, UUID setId, PredeterminadoRequest datos) {
        buscar(id);
        exigir("sets_instrumentales", setId, "Set instrumental no encontrado");
        jdbc.sql("""
                        INSERT INTO sets_predeterminados_procedimiento
                            (procedimiento_id, set_instrumental_id, cantidad, es_requerido, notas)
                        VALUES (:procedimientoId, :otroId, :cantidad, :esRequerido, :notas)
                        ON CONFLICT (procedimiento_id, set_instrumental_id) DO UPDATE
                            SET cantidad = EXCLUDED.cantidad, es_requerido = EXCLUDED.es_requerido,
                                notas = EXCLUDED.notas
                        """)
                .params(parametrosPredeterminado(id, setId, datos))
                .update();
        auditoria.registrar("sets_predeterminados_procedimiento", id, AccionAuditoria.ASOCIAR, null,
                parametrosPredeterminado(id, setId, datos));
    }

    @Transactional
    public void quitarSet(UUID id, UUID setId) {
        buscar(id);
        borrarPredeterminado("sets_predeterminados_procedimiento", "set_instrumental_id", id, setId,
                "El set no está predeterminado para el procedimiento");
    }

    @Transactional
    public void ponerInstrumento(UUID id, UUID instrumentoId, PredeterminadoRequest datos) {
        buscar(id);
        exigir("instrumentos_quirurgicos", instrumentoId, "Instrumento no encontrado");
        jdbc.sql("""
                        INSERT INTO instrumentos_predeterminados_procedimiento
                            (procedimiento_id, instrumento_id, cantidad, es_requerido, notas)
                        VALUES (:procedimientoId, :otroId, :cantidad, :esRequerido, :notas)
                        ON CONFLICT (procedimiento_id, instrumento_id) DO UPDATE
                            SET cantidad = EXCLUDED.cantidad, es_requerido = EXCLUDED.es_requerido,
                                notas = EXCLUDED.notas
                        """)
                .params(parametrosPredeterminado(id, instrumentoId, datos))
                .update();
        auditoria.registrar("instrumentos_predeterminados_procedimiento", id, AccionAuditoria.ASOCIAR, null,
                parametrosPredeterminado(id, instrumentoId, datos));
    }

    @Transactional
    public void quitarInstrumento(UUID id, UUID instrumentoId) {
        buscar(id);
        borrarPredeterminado("instrumentos_predeterminados_procedimiento", "instrumento_id", id, instrumentoId,
                "El instrumento no está predeterminado para el procedimiento");
    }

    // ------------------------------------------------------------------ apoyo

    private ProcedimientoQuirurgico buscar(UUID id) {
        return procedimientos.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Procedimiento no encontrado: " + id));
    }

    private RolPredeterminadoProcedimiento buscarRol(UUID id, UUID rolPredId) {
        buscar(id);
        return rolesPredeterminados.findByIdAndProcedimientoId(rolPredId, id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Rol predeterminado no encontrado: " + rolPredId));
    }

    /** 404 claro en vez del 422 genérico de la FK. Las tablas son constantes internas, nunca entrada del usuario. */
    private void exigir(String tabla, UUID id, String mensaje) {
        boolean existe = jdbc.sql("SELECT EXISTS (SELECT 1 FROM " + tabla + " WHERE id = :id)")
                .param("id", id)
                .query(Boolean.class)
                .single();
        if (!existe) {
            throw new RecursoNoEncontradoException(mensaje + ": " + id);
        }
    }

    private void borrarPredeterminado(String tabla, String columna, UUID id, UUID otroId, String mensaje) {
        int borradas = jdbc.sql("DELETE FROM " + tabla + " WHERE procedimiento_id = :procedimientoId AND "
                        + columna + " = :otroId")
                .param("procedimientoId", id)
                .param("otroId", otroId)
                .update();
        if (borradas == 0) {
            throw new RecursoNoEncontradoException(mensaje);
        }
        auditoria.registrar(tabla, id, AccionAuditoria.DESASOCIAR, Map.of(columna, otroId), null);
    }

    private static Map<String, Object> parametrosPredeterminado(UUID id, UUID otroId,
            PredeterminadoRequest datos) {
        Map<String, Object> p = new HashMap<>();
        p.put("procedimientoId", id);
        p.put("otroId", otroId);
        p.put("cantidad", datos.cantidad() == null ? 1 : datos.cantidad());
        p.put("esRequerido", datos.esRequerido() == null || datos.esRequerido());
        p.put("notas", Textos.limpiar(datos.notas()));
        return p;
    }

    private static void aplicar(ProcedimientoQuirurgico p, ProcedimientoRequest datos) {
        p.setCodigo(Textos.codigo(datos.codigo()));
        p.setNombre(Textos.limpiar(datos.nombre()));
        p.setDescripcion(Textos.limpiar(datos.descripcion()));
        p.setDuracionEstimadaMinutos(datos.duracionEstimadaMinutos());
        if (datos.activo() != null) {
            p.setActivo(datos.activo());
        }
    }

    private static void aplicar(RolPredeterminadoProcedimiento rol, RolPredeterminadoRequest datos) {
        rol.setRolClinicoId(datos.rolClinicoId());
        rol.setEspecialidadId(datos.especialidadId());
        rol.setCantidadPredeterminada(datos.cantidadPredeterminada() == null ? 1 : datos.cantidadPredeterminada());
        rol.setEsRequerido(datos.esRequerido() == null || datos.esRequerido());
        rol.setNotas(Textos.limpiar(datos.notas()));
    }

    // ------------------------------------------------------------------ lecturas del detalle

    private static final String SELECT_ROLES = """
            SELECT r.id, r.rol_clinico_id, rc.codigo AS rol_codigo, rc.nombre AS rol_nombre,
                   r.especialidad_id, e.codigo AS esp_codigo, e.nombre AS esp_nombre,
                   r.cantidad_predeterminada, r.es_requerido, r.notas
            FROM roles_predeterminados_procedimiento r
                     JOIN roles_clinicos rc ON rc.id = r.rol_clinico_id
                     LEFT JOIN especialidades e ON e.id = r.especialidad_id
            """;

    private List<RolPredeterminadoResponse> rolesPredeterminados(UUID procedimientoId) {
        return jdbc.sql(SELECT_ROLES + " WHERE r.procedimiento_id = :id ORDER BY r.creado_en, r.id")
                .param("id", procedimientoId)
                .query((rs, n) -> rol(rs))
                .list();
    }

    private RolPredeterminadoResponse rolPredeterminado(UUID rolPredId) {
        return jdbc.sql(SELECT_ROLES + " WHERE r.id = :id")
                .param("id", rolPredId)
                .query((rs, n) -> rol(rs))
                .single();
    }

    private static RolPredeterminadoResponse rol(ResultSet rs) throws SQLException {
        return new RolPredeterminadoResponse(uuid(rs, "id"), uuid(rs, "rol_clinico_id"), rs.getString("rol_codigo"),
                rs.getString("rol_nombre"), uuid(rs, "especialidad_id"), rs.getString("esp_codigo"),
                rs.getString("esp_nombre"), rs.getInt("cantidad_predeterminada"), rs.getBoolean("es_requerido"),
                rs.getString("notas"));
    }

    private List<ProcedimientoDetalleResponse.Referencia> especialidades(UUID procedimientoId) {
        return jdbc.sql("""
                        SELECT e.id, e.codigo, e.nombre
                        FROM procedimiento_especialidades pe
                                 JOIN especialidades e ON e.id = pe.especialidad_id
                        WHERE pe.procedimiento_id = :id
                        ORDER BY e.nombre
                        """)
                .param("id", procedimientoId)
                .query((rs, n) -> new ProcedimientoDetalleResponse.Referencia(uuid(rs, "id"), rs.getString("codigo"),
                        rs.getString("nombre")))
                .list();
    }

    private List<ProcedimientoDetalleResponse.Plantilla> plantillas(UUID procedimientoId) {
        return jdbc.sql("""
                        SELECT p.id, p.codigo, p.nombre, p.version, p.estado::text AS estado, ppc.es_predeterminada
                        FROM procedimiento_plantillas_checklist ppc
                                 JOIN plantillas_checklist p ON p.id = ppc.plantilla_checklist_id
                        WHERE ppc.procedimiento_id = :id
                        ORDER BY ppc.es_predeterminada DESC, p.codigo, p.version DESC
                        """)
                .param("id", procedimientoId)
                .query((rs, n) -> new ProcedimientoDetalleResponse.Plantilla(uuid(rs, "id"), rs.getString("codigo"),
                        rs.getString("nombre"), rs.getInt("version"), rs.getString("estado"),
                        rs.getBoolean("es_predeterminada")))
                .list();
    }

    private List<ProcedimientoDetalleResponse.Predeterminado> setsPredeterminados(UUID procedimientoId) {
        return jdbc.sql("""
                        SELECT s.id, s.codigo, s.nombre, sp.cantidad, sp.es_requerido, sp.notas
                        FROM sets_predeterminados_procedimiento sp
                                 JOIN sets_instrumentales s ON s.id = sp.set_instrumental_id
                        WHERE sp.procedimiento_id = :id
                        ORDER BY s.codigo
                        """)
                .param("id", procedimientoId)
                .query((rs, n) -> predeterminado(rs))
                .list();
    }

    private List<ProcedimientoDetalleResponse.Predeterminado> instrumentosPredeterminados(UUID procedimientoId) {
        return jdbc.sql("""
                        SELECT i.id, i.codigo, i.nombre, ip.cantidad, ip.es_requerido, ip.notas
                        FROM instrumentos_predeterminados_procedimiento ip
                                 JOIN instrumentos_quirurgicos i ON i.id = ip.instrumento_id
                        WHERE ip.procedimiento_id = :id
                        ORDER BY i.codigo
                        """)
                .param("id", procedimientoId)
                .query((rs, n) -> predeterminado(rs))
                .list();
    }

    private static ProcedimientoDetalleResponse.Predeterminado predeterminado(ResultSet rs) throws SQLException {
        return new ProcedimientoDetalleResponse.Predeterminado(uuid(rs, "id"), rs.getString("codigo"),
                rs.getString("nombre"), rs.getInt("cantidad"), rs.getBoolean("es_requerido"), rs.getString("notas"));
    }
}
