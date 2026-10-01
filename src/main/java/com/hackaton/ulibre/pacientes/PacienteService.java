package com.hackaton.ulibre.pacientes;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;
import java.util.UUID;

import com.hackaton.ulibre.auditoria.AccionAuditoria;
import com.hackaton.ulibre.auditoria.Auditoria;
import com.hackaton.ulibre.comun.Pagina;
import com.hackaton.ulibre.comun.Paginacion;
import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.ReglaNegocioException;
import com.hackaton.ulibre.comun.Textos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pacientes. Cada alta, edición o borrado queda en registros_auditoria. */
@Service
public class PacienteService {

    private static final String TABLA = "pacientes";

    private final PacienteRepository pacientes;
    private final AlergiaPacienteRepository alergias;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final Auditoria auditoria;

    public PacienteService(PacienteRepository pacientes, AlergiaPacienteRepository alergias, JdbcClient jdbc,
            Clock clock, Auditoria auditoria) {
        this.pacientes = pacientes;
        this.alergias = alergias;
        this.jdbc = jdbc;
        this.clock = clock;
        this.auditoria = auditoria;
    }

    /** q busca en nombres, apellidos, nombre completo y número de documento, sin distinguir mayúsculas. */
    @Transactional(readOnly = true)
    public Pagina<PacienteResumen> listar(String q, int pagina, int tamano) {
        PageRequest pedido = Paginacion.de(pagina, tamano).pageRequest(Sort.by("apellidos", "nombres"));
        String patron = Textos.patronLike(q);
        Page<Paciente> page = patron == null ? pacientes.findAll(pedido)
                : pacientes.buscar(patron.toLowerCase(Locale.ROOT), pedido);
        return Pagina.de(page, p -> new PacienteResumen(p.getId(), p.getNombres(), p.getApellidos(),
                p.getTipoDocumento(), p.getNumeroDocumento(), p.getFechaNacimiento(), edad(p.getFechaNacimiento()),
                p.getUsuarioId() != null));
    }

    @Transactional(readOnly = true)
    public PacienteResponse obtener(UUID id) {
        return respuesta(buscar(id));
    }

    @Transactional
    public PacienteResponse crear(PacienteRequest datos) {
        Paciente paciente = new Paciente();
        aplicar(paciente, datos);
        PacienteResponse creado = respuesta(pacientes.saveAndFlush(paciente));
        auditoria.registrar(TABLA, creado.id(), AccionAuditoria.CREAR, null, creado);
        return creado;
    }

    @Transactional
    public PacienteResponse actualizar(UUID id, PacienteRequest datos) {
        Paciente paciente = buscar(id);
        PacienteResponse anterior = respuesta(paciente);
        aplicar(paciente, datos);
        PacienteResponse actualizado = respuesta(pacientes.saveAndFlush(paciente));
        auditoria.registrar(TABLA, id, AccionAuditoria.ACTUALIZAR, anterior, actualizado);
        return actualizado;
    }

    /**
     * Borrado físico solo para registros sin histórico (p. ej. creados por error). Con citas o
     * solicitudes no se borra; sus alergias, que son dato maestro, se borran con él.
     */
    @Transactional
    public void eliminar(UUID id) {
        Paciente paciente = buscar(id);
        boolean tieneHistorico = jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM citas WHERE paciente_id = :id)
                            OR EXISTS (SELECT 1 FROM solicitudes_cirugia WHERE paciente_id = :id)
                        """)
                .param("id", id)
                .query(Boolean.class)
                .single();
        if (tieneHistorico) {
            throw new ReglaNegocioException("El paciente tiene histórico clínico (citas o solicitudes); no se borra");
        }
        PacienteResponse anterior = respuesta(paciente);
        alergias.deleteAllByPacienteId(id);
        pacientes.delete(paciente);
        pacientes.flush();
        auditoria.registrar(TABLA, id, AccionAuditoria.ELIMINAR, anterior, null);
    }

    @Transactional(readOnly = true)
    public Paciente buscar(UUID id) {
        return pacientes.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado: " + id));
    }

    private void aplicar(Paciente paciente, PacienteRequest datos) {
        if (datos.usuarioId() != null && !existeUsuario(datos.usuarioId())) {
            throw new RecursoNoEncontradoException("Usuario no encontrado: " + datos.usuarioId());
        }
        paciente.setUsuarioId(datos.usuarioId());
        paciente.setNombres(Textos.limpiar(datos.nombres()));
        paciente.setApellidos(Textos.limpiar(datos.apellidos()));
        paciente.setTipoDocumento(Textos.codigo(datos.tipoDocumento()));
        paciente.setNumeroDocumento(Textos.limpiar(datos.numeroDocumento()));
        paciente.setFechaNacimiento(datos.fechaNacimiento());
        paciente.setTelefono(Textos.limpiar(datos.telefono()));
        paciente.setCorreo(Textos.correo(datos.correo()));
        paciente.setContactoEmergenciaNombre(Textos.limpiar(datos.contactoEmergenciaNombre()));
        paciente.setContactoEmergenciaTelefono(Textos.limpiar(datos.contactoEmergenciaTelefono()));
    }

    private boolean existeUsuario(UUID usuarioId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM usuarios WHERE id = :id)")
                .param("id", usuarioId)
                .query(Boolean.class)
                .single();
    }

    private PacienteResponse respuesta(Paciente p) {
        return new PacienteResponse(p.getId(), p.getUsuarioId(), p.getNombres(), p.getApellidos(),
                p.getTipoDocumento(), p.getNumeroDocumento(), p.getFechaNacimiento(), edad(p.getFechaNacimiento()),
                p.getTelefono(), p.getCorreo(), p.getContactoEmergenciaNombre(), p.getContactoEmergenciaTelefono(),
                alergias.findAllByPacienteIdAndActivoOrderBySustancia(p.getId(), true).stream()
                        .map(AlergiaResponse::de)
                        .toList(),
                p.getCreadoEn(), p.getActualizadoEn());
    }

    private Integer edad(LocalDate nacimiento) {
        return nacimiento == null ? null : Period.between(nacimiento, LocalDate.now(clock)).getYears();
    }
}
