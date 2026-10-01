package com.hackaton.ulibre.comun;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hackaton.ulibre.auth.CredencialesInvalidasException;
import jakarta.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce errores a application/problem+json.
 *
 * <p>Las reglas de negocio viven en la base (constraints y triggers). Aquí solo se desenvuelve la
 * excepción hasta el SQLException de PostgreSQL y se mapea su SQLState a un estado HTTP; el
 * "detail" es el mensaje del trigger/constraint.
 */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    private static final Map<String, HttpStatus> ESTADO_POR_SQLSTATE = Map.of(
            "23514", HttpStatus.UNPROCESSABLE_CONTENT,   // check_violation (y RAISE ... USING ERRCODE = 'check_violation')
            "23P01", HttpStatus.CONFLICT,                // exclusion_violation (solapes)
            "23505", HttpStatus.CONFLICT,                // unique_violation
            "23503", HttpStatus.UNPROCESSABLE_CONTENT,   // foreign_key_violation
            "23502", HttpStatus.UNPROCESSABLE_CONTENT,   // not_null_violation
            "22001", HttpStatus.UNPROCESSABLE_CONTENT,   // string_data_right_truncation (texto más largo que la columna)
            "22003", HttpStatus.UNPROCESSABLE_CONTENT,   // numeric_value_out_of_range
            "P0001", HttpStatus.UNPROCESSABLE_CONTENT);  // raise_exception (RAISE sin código)

    private static final Map<String, String> TITULO_POR_SQLSTATE = Map.of(
            "23514", "Regla de negocio incumplida",
            "23P01", "Conflicto de horario",
            "23505", "Registro duplicado",
            "23503", "Referencia inválida",
            "23502", "Dato obligatorio faltante",
            "22001", "Dato demasiado largo",
            "22003", "Valor numérico fuera de rango",
            "P0001", "Regla de negocio incumplida");

    /** Prefijo de severidad del servidor ("ERROR:  ") que antepone el driver. */
    private static final Pattern PREFIJO_SEVERIDAD = Pattern.compile("^[A-ZÁÉÍÓÚ]+:\s+");
    private static final Pattern NOMBRE_RESTRICCION = Pattern.compile("constraint \"([^\"]+)\"");

    @ExceptionHandler({DataAccessException.class, PersistenceException.class, TransactionSystemException.class})
    public ResponseEntity<ProblemDetail> manejarErrorDeBase(RuntimeException ex) {
        SQLException sql = buscarSqlException(ex);
        HttpStatus estado = sql == null ? null : ESTADO_POR_SQLSTATE.get(sql.getSQLState());
        if (estado == null) {
            log.error("Error de acceso a datos no mapeado", ex);
            return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                    "Ocurrió un error inesperado al acceder a los datos");
        }

        String mensaje = mensajeDelServidor(sql);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, mensaje);
        problema.setTitle(TITULO_POR_SQLSTATE.get(sql.getSQLState()));
        problema.setProperty("codigoSql", sql.getSQLState());
        Matcher restriccion = NOMBRE_RESTRICCION.matcher(mensaje);
        if (restriccion.find()) {
            problema.setProperty("restriccion", restriccion.group(1));
        }
        return ResponseEntity.status(estado).body(problema);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ProblemDetail> manejarNoEncontrado(RecursoNoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage());
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ProblemDetail> manejarReglaNegocio(ReglaNegocioException ex) {
        return problema(HttpStatus.UNPROCESSABLE_CONTENT, "Regla de negocio incumplida", ex.getMessage());
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ProblemDetail> manejarCredenciales(CredencialesInvalidasException ex) {
        return problema(HttpStatus.UNAUTHORIZED, "No autenticado", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> manejarAccesoDenegado(AccessDeniedException ex) {
        return problema(HttpStatus.FORBIDDEN, "Acceso denegado", "No tiene permiso para realizar esta acción");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("campo", e.getField(),
                        "mensaje", e.getDefaultMessage() == null ? "Valor inválido" : e.getDefaultMessage()))
                .toList();
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "La solicitud tiene campos inválidos");
        problema.setTitle("Datos inválidos");
        problema.setProperty("errores", errores);
        return ResponseEntity.badRequest().body(problema);
    }

    /** Restricciones sobre parámetros (@PathVariable, @RequestParam), con la misma forma que el cuerpo. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errores = ex.getParameterValidationResults().stream()
                .flatMap(resultado -> resultado.getResolvableErrors().stream()
                        .map(e -> Map.of(
                                "campo", e instanceof FieldError campo ? campo.getField()
                                        : String.valueOf(resultado.getMethodParameter().getParameterName()),
                                "mensaje", e.getDefaultMessage() == null ? "Valor inválido" : e.getDefaultMessage())))
                .toList();
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "La solicitud tiene parámetros inválidos");
        problema.setTitle("Datos inválidos");
        problema.setProperty("errores", errores);
        return ResponseEntity.badRequest().body(problema);
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return ResponseEntity.status(estado).body(problema);
    }

    /** Recorre la cadena de causas (y las "next exception" de los batch) hasta el SQLException con SQLState. */
    private static SQLException buscarSqlException(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof BatchUpdateException batch && batch.getNextException() != null) {
                return batch.getNextException();
            }
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return null;
    }

    /**
     * Primera línea del mensaje del servidor, sin el prefijo de severidad: es el texto del
     * RAISE del trigger o la violación del constraint. Se descartan las líneas Detail/Where,
     * que pueden contener valores de datos clínicos.
     */
    private static String mensajeDelServidor(SQLException sql) {
        String mensaje = sql.getMessage() == null ? "" : sql.getMessage();
        int salto = mensaje.indexOf('\n');
        if (salto >= 0) {
            mensaje = mensaje.substring(0, salto);
        }
        return PREFIJO_SEVERIDAD.matcher(mensaje.strip()).replaceFirst("");
    }
}
