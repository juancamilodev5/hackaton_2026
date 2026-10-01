package com.hackaton.ulibre.citas;

import java.util.Optional;
import java.util.UUID;

import com.hackaton.ulibre.comun.Pagina;
import com.hackaton.ulibre.comun.Paginacion;
import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.ReglaNegocioException;
import com.hackaton.ulibre.comun.Textos;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Las citas no se borran: se cancelan (PATCH /estado). */
@Service
public class CitaService {

    private final CitaRepository citas;
    private final CitasConsultas consultas;
    private final JdbcClient jdbc;

    public CitaService(CitaRepository citas, CitasConsultas consultas, JdbcClient jdbc) {
        this.citas = citas;
        this.consultas = consultas;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Pagina<CitaResponse> listar(CitasConsultas.Filtro filtro, int pagina, int tamano) {
        return consultas.listar(filtro, Paginacion.de(pagina, tamano));
    }

    @Transactional(readOnly = true)
    public CitaResponse obtener(UUID id) {
        return consultas.obtener(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada: " + id));
    }

    @Transactional
    public CitaResponse crear(CitaRequest datos) {
        validarReferencias(datos);
        Cita cita = new Cita();
        cita.setEstado(EstadoCita.SOLICITADA);
        aplicar(cita, datos);
        return obtener(citas.saveAndFlush(cita).getId());
    }

    @Transactional
    public CitaResponse actualizar(UUID id, CitaRequest datos) {
        Cita cita = buscar(id);
        if (!cita.getEstado().admiteEdicion()) {
            throw new ReglaNegocioException("La cita está " + cita.getEstado() + " y ya no se puede modificar");
        }
        validarReferencias(datos);
        aplicar(cita, datos);
        citas.saveAndFlush(cita);
        return obtener(id);
    }

    @Transactional
    public CitaResponse cambiarEstado(UUID id, EstadoCita destino) {
        Cita cita = buscar(id);
        if (!cita.getEstado().puedePasarA(destino)) {
            throw new ReglaNegocioException("Transición de estado no permitida: " + cita.getEstado() + " → " + destino);
        }
        cita.setEstado(destino);
        citas.saveAndFlush(cita);
        return obtener(id);
    }

    private Cita buscar(UUID id) {
        return citas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada: " + id));
    }

    private static void aplicar(Cita cita, CitaRequest datos) {
        cita.setPacienteId(datos.pacienteId());
        cita.setMedicoId(datos.medicoId());
        cita.setEspecialidadId(datos.especialidadId());
        cita.setProgramadaPara(datos.programadaPara());
        cita.setMotivo(Textos.limpiar(datos.motivo()));
    }

    /** La base solo exige que existan; activo y especialidad del médico se comprueban aquí. */
    private void validarReferencias(CitaRequest datos) {
        if (!existe("SELECT EXISTS (SELECT 1 FROM pacientes WHERE id = :id)", datos.pacienteId())) {
            throw new RecursoNoEncontradoException("Paciente no encontrado: " + datos.pacienteId());
        }
        Boolean especialidadActiva = activo("SELECT activo FROM especialidades WHERE id = :id",
                datos.especialidadId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Especialidad no encontrada: " + datos.especialidadId()));
        if (!especialidadActiva) {
            throw new ReglaNegocioException("La especialidad está inactiva");
        }
        Boolean medicoActivo = activo("SELECT activo FROM perfiles_profesionales WHERE id = :id", datos.medicoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Médico no encontrado: " + datos.medicoId()));
        if (!medicoActivo) {
            throw new ReglaNegocioException("El médico no está activo");
        }
        boolean tieneEspecialidad = jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM profesional_especialidades
                                       WHERE profesional_id = :medico AND especialidad_id = :especialidad)
                        """)
                .param("medico", datos.medicoId())
                .param("especialidad", datos.especialidadId())
                .query(Boolean.class)
                .single();
        if (!tieneEspecialidad) {
            throw new ReglaNegocioException("El médico no tiene registrada la especialidad de la cita");
        }
    }

    private boolean existe(String sql, UUID id) {
        return jdbc.sql(sql).param("id", id).query(Boolean.class).single();
    }

    private Optional<Boolean> activo(String sql, UUID id) {
        return jdbc.sql(sql).param("id", id).query(Boolean.class).optional();
    }
}
