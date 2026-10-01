# Iteración 1 — Base del backend (lectura del tablero)

Fecha: 2026-10-01 · Spring Boot 4.1.1 · Java 25 · PostgreSQL 18 · Hibernate 7.4 · Flyway 12

Resumen de lo implementado sobre el proyecto vacío. Fuente de verdad del modelo:
`docs/db/esquema_seguridad_quirurgica_v4.sql` (manda sobre `doc-maestro` y el DBML cuando difieren;
p. ej. el maestro pide `TIMESTAMPTZ` en UTC y la V4 usa `timestamp` en hora de Bogotá).

---

## 1. Infraestructura local

| Archivo | Contenido |
|---|---|
| `docker-compose.yml` | PostgreSQL 18, contenedor `ulibre-postgres`, volumen nombrado `ulibre-pgdata`, healthcheck. Variables con valores por defecto: `DB_NAME`, `DB_USER`, `DB_PASSWORD` (`ulibre`), `DB_PORT` (`5432`). |
| `src/main/resources/application.yml` | Perfil **default**: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` obligatorios; solo `db/migration`. Perfil **dev**: valores por defecto de desarrollo y además `db/demo`. `open-in-view=false`, `ddl-auto=validate`, ProblemDetails activados, `CORS_ORIGINS` configurable. |

## 2. Migraciones Flyway

| Migración | Origen |
|---|---|
| `db/migration/V1__esquema.sql` | Copia de `esquema_seguridad_quirurgica_v4.sql` sin `BEGIN;`/`COMMIT;` (solo se quitaron esas 2 líneas). |
| `db/migration/V2__semilla_estructural.sql` | Copia de `semilla_estructural_v4.sql` sin `BEGIN;`/`COMMIT;`. |
| `db/migration/V3__auth_y_alertas.sql` | `usuarios.hash_contrasena varchar(100)` + índice único parcial `ux_alerta_vigente_por_regla` (una alerta vigente por regla y cirugía). |
| `db/demo/V100__datos_demo.sql` | Copia de `datos_demo_v4.sql` sin `BEGIN;`/`COMMIT;` + `pgcrypto`, usuario `admin@demo.local` (ADMIN) y `hash_contrasena = crypt('demo1234', gen_salt('bf', 10))` para todos los usuarios demo (formato `$2a$`, compatible con `BCryptPasswordEncoder`). |

No fue necesario modificar V1/V2 ni crear una V4.

## 3. Paquetes (organización por funcionalidad)

```
com.hackaton.ulibre
├── UlibreApplication      TimeZone.setDefault(America/Bogota) antes de levantar el contexto
├── comun                  ZonaHoraria, RelojConfig (Clock), ManejadorErrores, Filas, OpenApiConfig,
│                          RecursoNoEncontradoException
├── auth                   Usuario, login/me, JWT, SecurityConfig, CORS, permisos
├── catalogos              RolClinicoVista, QuirofanoVista
├── cirugias               Cirugia, AsignacionPersonalCirugia, DatosPreoperatoriosCirugia,
│                          GET /api/cirugias, cabecera y equipo del tablero
├── checklist              Checklist/Fase/Item/Confirmación de cirugía + ChecklistConsultas
├── instrumental           SetInstrumentalCirugia, InstrumentoCirugia + InstrumentalConsultas
├── alertas                Alerta + AlertasConsultas
└── tablero                RecuentoCirugia, ConfirmacionRecuento, HitoCirugia,
                           GET /api/cirugias/{id}/tablero
```

## 4. Zona horaria y reloj (`comun`)

- La JVM fija `America/Bogota` en `main`; el driver JDBC envía esa zona a PostgreSQL.
- Bean `Clock` en `America/Bogota`. Todo "ahora" del backend usa `LocalDateTime.now(clock)` /
  `LocalDate.now(clock)` / `clock.instant()`.

## 5. Manejo de errores (`comun/ManejadorErrores`)

`@RestControllerAdvice` (extiende `ResponseEntityExceptionHandler`) que responde `application/problem+json`.

- Desenvuelve `DataAccessException`, `PersistenceException` y `TransactionSystemException` hasta el
  `SQLException` y mapea por SQLState:

  | SQLState | Significado | HTTP |
  |---|---|---|
  | `23514` | check_violation (también RAISE de triggers con ese código) | 422 |
  | `23P01` | exclusion_violation (solapes) | 409 |
  | `23505` | unique_violation | 409 |
  | `23503` | foreign_key_violation | 422 |
  | `23502` | not_null_violation (añadido) | 422 |
  | `P0001` | raise_exception (RAISE sin código) | 422 |

- `detail` = primera línea del mensaje del servidor (texto del trigger/constraint) sin el prefijo
  `ERROR:`; se descartan las líneas `Detail`/`Where` para no exponer datos clínicos. Propiedades extra:
  `codigoSql` y `restriccion` (nombre del constraint, si aparece).
- `@Valid` → 400 con `errores: [{campo, mensaje}]`.
- Recurso inexistente → 404 · credenciales inválidas → 401 · sin permiso → 403.
- Solo un error de datos **no** mapeado produce 500 (y se registra en el log).

## 6. Autenticación JWT (`auth`)

- `JwtProperties` (`app.jwt.secret`, `app.jwt.expiracion` = 8h): la app no arranca si el secreto falta
  o tiene menos de 32 bytes.
- `NimbusJwtEncoder` con `ImmutableSecret`; `NimbusJwtDecoder.withSecretKey(...).macAlgorithm(HS256)`
  con validación de emisor `ulibre`.
- `POST /api/auth/login {correo, contrasena}`: usuario `ACTIVO` + BCrypt. Respuesta
  `{token, expiraEn, usuario{id, nombres, apellidos, correo, roles[], permisos[]}}`.
  Claims: `iss`, `sub` (id), `iat`, `exp`, `correo`, `roles`, `permisos`.
  Error siempre 401 genérico; si el correo no existe se compara contra un hash ficticio para no
  delatarlo por tiempo de respuesta.
- `GET /api/auth/me`: datos del usuario con roles/permisos frescos de la base.
- Permisos: `usuario_roles_sistema → rol_sistema_permisos → permisos`, solo roles activos.
- `SecurityFilterChain`: stateless, CSRF deshabilitado, CORS desde `CORS_ORIGINS`
  (por defecto `http://localhost:5173`, `http://localhost:3000`). Públicos: `/api/auth/login`,
  `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/error`.
- `JwtAuthenticationConverter`: claim `permisos` → authorities sin prefijo
  (`@PreAuthorize("hasAuthority('TABLERO_VER')")`). Method security habilitada.
- 401 del filtro con `WWW-Authenticate` estándar y cuerpo problem+json.

## 7. Entidades JPA (14, delgadas)

`Usuario`, `Cirugia`, `AsignacionPersonalCirugia`, `DatosPreoperatoriosCirugia`, `ChecklistCirugia`,
`FaseChecklistCirugia`, `ItemChecklistCirugia`, `ConfirmacionItemChecklist`, `SetInstrumentalCirugia`,
`InstrumentoCirugia`, `Alerta`, `RecuentoCirugia`, `ConfirmacionRecuento`, `HitoCirugia`.

Convenciones aplicadas:

- FKs como columnas `UUID`, sin `@ManyToOne` ni relaciones bidireccionales.
- Enums de PostgreSQL: `@Enumerated(STRING)` + `@JdbcTypeCode(NAMED_ENUM)` +
  `@Column(columnDefinition = "<tipo_pg>")` (necesario para que `validate` reconozca el tipo).
  Enums Java con los mismos valores que el SQL.
- `jsonb`: `@JdbcTypeCode(SqlTypes.JSON)` sobre `String` (JSON crudo).
- Fechas `LocalDateTime`; `creado_en`/`actualizado_en` con `@Generated` (los pone la base).
- IDs: `@UuidGenerator(style = VERSION_7)`.
- `@DynamicUpdate` en todas: evita que los triggers `UPDATE OF columna` se disparen con columnas
  que no cambiaron.
- Lombok `@Getter/@Setter` solo en entidades; DTOs como records.
- Ninguna regla de negocio de la base se reimplementó en Java (los comentarios de cada entidad
  indican qué trigger/constraint la protege).

## 8. Lecturas del tablero

Implementadas con SQL nativo (`JdbcClient`) hacia records, sin grafos de entidades.

### `GET /api/cirugias?fecha=YYYY-MM-DD` — permiso `CIRUGIAS_VER`

Una sola consulta. Por defecto la fecha es hoy (reloj de Bogotá); filtra por `inicio_programado`
dentro del día. Devuelve por cirugía: id, paciente, procedimiento (+ sitio y lateralidad), quirófano,
inicio/fin programado, estado, `alertasBloqueantesAbiertas` (ABIERTA/RECONOCIDA, bloqueantes,
**sin excepción autorizada**), `operadorTablero` (nombre o null), `equipoCompleto`.

### `GET /api/cirugias/{id}/tablero` — permiso `TABLERO_VER`

`TableroService` compone en una transacción de solo lectura `REPEATABLE READ` (todas las consultas
ven la misma foto). **14 consultas fijas**, sin N+1:

| Sección | Detalle |
|---|---|
| `cirugia` | id, estado, quirófano, inicio/fin programado |
| `paciente` | nombre completo, tipo y número de documento, fecha de nacimiento, **edad calculada** con el reloj |
| `procedimiento` | código, nombre, sitio quirúrgico, lateralidad |
| `preoperatorio` | peso, talla, glucometría, reserva de sangre, alergias desde `copia_alergias` (snapshot), información clínica, validado por/en; null si no existe |
| `equipo` | por requerimiento: rol, especialidad, cantidad requerida, cubiertos, asignaciones vigentes (asignacionId, profesional, estado, esOperadorTablero); `equipoCompleto` |
| `operadorTablero` | asignacionId + nombre, o null |
| `checklist` | null si no se ha iniciado; plantilla (código, nombre, versión), estado, fases ordenadas (estado, cerrada por nombre + rol / en) → ítems ordenados (código, etiqueta, tipo de respuesta, obligatorio, bloqueante, rol responsable, estado, respuesta JSON cruda, registrado por/en) → confirmaciones (persona, rol con que participa, resultado, confirmado en) |
| `instrumental` | sets (requerido/preparado, preparado por/en, instrumentos del set), instrumentos directos, `instrumentalCompleto` y `faltantes` (mismo criterio que `fn_exigir_instrumental_completo`) |
| `recuentos` | desde `v_recuentos_cirugia`: tipo, descripción, inicial, agregada, final, esperada, estado calculado, registrado por/en, confirmaciones por etapa `{INICIAL: [], FINAL: []}` |
| `alertas` | vigentes primero, luego severidad y fecha; regla, severidad, bloqueante, estado, título, mensaje, disparada en, reconocida/resuelta por, `excepcion {autorizadaPor, autorizadaEn, motivo}` |
| `hitos` | solo vigentes (`anulado_en IS NULL`), ordenados por `ocurrido_en` |

Los participantes ("quién y en qué rol") se cargan una vez por cirugía, incluidas las asignaciones
canceladas, porque pueden haber firmado registros históricos. 404 si la cirugía no existe.

## 9. OpenAPI

springdoc 3.0.0 con esquema de seguridad HTTP bearer JWT (`bearer-jwt`) aplicado globalmente; el
login aparece sin candado. Swagger UI en `/swagger-ui.html`.

## 10. README

Cómo levantar (`docker compose up -d`, variables de entorno, `./mvnw spring-boot:run
-Dspring-boot.run.profiles=dev`), requisito de que el usuario sea dueño de la base, usuarios demo
(todos con `demo1234`), endpoints, tabla de errores y organización del código.

---

## Verificación manual realizada

PostgreSQL 18.6 en Docker (puerto 5433) + app con perfil dev:

| Prueba | Resultado |
|---|---|
| Flyway V1, V2, V3, V100 | aplicadas sin errores |
| Hibernate `validate` | sin errores (la app arrancó) |
| Login `carlos.perez@demo.local` / `demo1234` | 200, token con roles y permisos |
| `GET /api/auth/me` | 200 |
| `GET /api/cirugias` (hoy) | 200, 0 cirugías |
| `GET /api/cirugias?fecha=<mañana>` | 200, 1 cirugía (bypass 07:00–11:00), operador Carlos Pérez, `equipoCompleto=false` |
| `GET /api/cirugias/{id}/tablero` | 200: 3 fases, 19 ítems, 5 asignaciones con Carlos como operador, `equipoCompleto=false` (AYUDANTE_QUIRURGICO 0/2), `instrumentalCompleto=false` (faltan SET_VASCULAR y SEPARADOR_ESTERNAL), edad 64, alergia a penicilina |
| Tablero de cirugía inexistente / id no UUID | 404 / 400 |
| Contraseña errónea / correo inexistente | 401 genérico |
| Sin token / token alterado | 401 |
| Usuario sin permiso (paciente) | 403 |
| Login con campos inválidos | 400 con lista de campos |
| CORS `localhost:5173` / origen ajeno | permitido / 403 |
| Swagger UI y `/v3/api-docs` | 200, esquema `bearer-jwt` |
| Perfil default sin `JWT_SECRET` o con uno corto | la app no arranca |

**Pendiente de verificar:** la traducción de errores de base a 422/409 (no hay aún endpoints de
escritura que disparen los triggers).

## Notas del entorno

- El puerto 5432 puede estar ocupado por un PostgreSQL local: usar `DB_PORT=5433` y
  `DB_URL=jdbc:postgresql://localhost:5433/ulibre`.
- Variables heredadas en la máquina (`DB_USER`, `DB_PASSWORD`, `DB_NAME`, `SPRING_PROFILES_ACTIVE`)
  las toman tanto `docker compose` como la app; definirlas explícitamente o en `.env`.
- Compilar con JDK 25 (`JAVA_HOME`).
- `UlibreApplicationTests` (test generado por defecto) necesita base de datos: `mvn package` sin base
  requiere `-DskipTests`.

## Fuera de alcance (siguiente iteración)

Acciones de escritura del tablero (registrar/confirmar ítems, cerrar fases, hitos, recuentos,
alertas), motor de reglas, panel de indicadores, solicitudes, programación, asignación de personal y
citas. Las funciones `fn_iniciar_checklist` y `fn_preparar_instrumental` se invocarán con consultas
nativas.
