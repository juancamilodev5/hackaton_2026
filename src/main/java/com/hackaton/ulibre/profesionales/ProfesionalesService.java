package com.hackaton.ulibre.profesionales;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.hackaton.ulibre.auth.UsuarioRepository;
import com.hackaton.ulibre.comun.Pagina;
import com.hackaton.ulibre.comun.Paginacion;
import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.ReglaNegocioException;
import com.hackaton.ulibre.comun.Textos;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfiles profesionales y sus roles clínicos y especialidades. Un perfil no se borra: puede estar
 * en asignaciones y solicitudes históricas; "eliminar" lo deja inactivo.
 */
@Service
public class ProfesionalesService {

    private final PerfilProfesionalRepository perfiles;
    private final UsuarioRepository usuarios;
    private final ProfesionalesConsultas consultas;
    private final JdbcClient jdbc;

    public ProfesionalesService(PerfilProfesionalRepository perfiles, UsuarioRepository usuarios,
            ProfesionalesConsultas consultas, JdbcClient jdbc) {
        this.perfiles = perfiles;
        this.usuarios = usuarios;
        this.consultas = consultas;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Pagina<ProfesionalResponse> listar(Boolean activo, UUID rolClinicoId, UUID especialidadId, String q,
            int pagina, int tamano) {
        return consultas.listar(activo, rolClinicoId, especialidadId, Textos.limpiar(q), Paginacion.de(pagina, tamano));
    }

    @Transactional(readOnly = true)
    public ProfesionalResponse obtener(UUID id) {
        return consultas.obtener(id).orElseThrow(() -> noEncontrado(id));
    }

    @Transactional(readOnly = true)
    public List<ProfesionalDisponibleResponse> disponibles(LocalDateTime desde, LocalDateTime hasta,
            UUID rolClinicoId, UUID especialidadId) {
        if (!hasta.isAfter(desde)) {
            throw new ReglaNegocioException("El fin del rango debe ser posterior al inicio");
        }
        return consultas.disponibles(desde, hasta, rolClinicoId, especialidadId);
    }

    @Transactional
    public ProfesionalResponse crear(NuevoProfesionalRequest datos) {
        if (!usuarios.existsById(datos.usuarioId())) {
            throw new RecursoNoEncontradoException("Usuario no encontrado: " + datos.usuarioId());
        }
        PerfilProfesional perfil = new PerfilProfesional();
        perfil.setUsuarioId(datos.usuarioId());
        perfil.setLicenciaProfesional(Textos.limpiar(datos.licenciaProfesional()));
        perfil.setNumeroProfesional(Textos.limpiar(datos.numeroProfesional()));
        perfil.setActivo(true);
        perfiles.saveAndFlush(perfil);
        return obtener(perfil.getId());
    }

    @Transactional
    public ProfesionalResponse actualizar(UUID id, ProfesionalRequest datos) {
        PerfilProfesional perfil = buscar(id);
        perfil.setLicenciaProfesional(Textos.limpiar(datos.licenciaProfesional()));
        perfil.setNumeroProfesional(Textos.limpiar(datos.numeroProfesional()));
        if (datos.activo() != null) {
            perfil.setActivo(datos.activo());
        }
        perfiles.saveAndFlush(perfil);
        return obtener(id);
    }

    @Transactional
    public void desactivar(UUID id) {
        PerfilProfesional perfil = buscar(id);
        perfil.setActivo(false);
        perfiles.saveAndFlush(perfil);
    }

    @Transactional
    public ProfesionalResponse reemplazarRolesClinicos(UUID id, List<UUID> ids) {
        buscar(id);
        reemplazar(id, ids, "roles_clinicos", "profesional_roles_clinicos", "rol_clinico_id", "Roles clínicos");
        return obtener(id);
    }

    /** Si una solicitud referencia la especialidad (fk_solicitud_medico_especialidad), la base responde 422. */
    @Transactional
    public ProfesionalResponse reemplazarEspecialidades(UUID id, List<UUID> ids) {
        buscar(id);
        reemplazar(id, ids, "especialidades", "profesional_especialidades", "especialidad_id", "Especialidades");
        return obtener(id);
    }

    PerfilProfesional buscar(UUID id) {
        return perfiles.findById(id).orElseThrow(() -> noEncontrado(id));
    }

    /** Deja exactamente los ids indicados en la tabla puente: borra los que sobran e inserta los que faltan. */
    private void reemplazar(UUID profesionalId, List<UUID> ids, String catalogo, String puente, String columna,
            String descripcion) {
        Set<UUID> pedidos = new LinkedHashSet<>(ids);
        if (!pedidos.isEmpty()) {
            List<UUID> existentes = jdbc.sql("SELECT id FROM " + catalogo + " WHERE id IN (:ids)")
                    .param("ids", pedidos)
                    .query(UUID.class)
                    .list();
            List<String> inexistentes = pedidos.stream()
                    .filter(i -> !existentes.contains(i))
                    .map(UUID::toString)
                    .toList();
            if (!inexistentes.isEmpty()) {
                throw new ReglaNegocioException(descripcion + " inexistentes: " + String.join(", ", inexistentes));
            }
        }

        String borrar = "DELETE FROM " + puente + " WHERE profesional_id = :profesionalId"
                + (pedidos.isEmpty() ? "" : " AND " + columna + " NOT IN (:ids)");
        var sentencia = jdbc.sql(borrar).param("profesionalId", profesionalId);
        if (!pedidos.isEmpty()) {
            sentencia = sentencia.param("ids", pedidos);
        }
        sentencia.update();

        for (UUID valor : pedidos) {
            jdbc.sql("INSERT INTO " + puente + " (profesional_id, " + columna + ") VALUES (:profesionalId, :valor)"
                            + " ON CONFLICT DO NOTHING")
                    .param("profesionalId", profesionalId)
                    .param("valor", valor)
                    .update();
        }
    }

    private static RecursoNoEncontradoException noEncontrado(UUID id) {
        return new RecursoNoEncontradoException("Profesional no encontrado: " + id);
    }
}
