# hackaton_2026 — Plataforma de seguridad quirúrgica (backend)

Spring Boot 4.1 · Java 25 · PostgreSQL 18 · Flyway · JWT propio (HS256).

La base de datos implementa la mayoría de reglas de negocio (solapes de quirófano y personal,
roles clínicos, cupos, plantillas inmutables, append-only, operador del tablero, instrumental
obligatorio, fases con alertas bloqueantes, no borrado). El backend **no** las reimplementa:
traduce los errores de PostgreSQL a HTTP (`application/problem+json`).

Fuente de verdad del modelo: `src/main/java/com/hackaton/ulibre/docs/db/esquema_seguridad_quirurgica_v4.sql`
(si el documento maestro o el DBML lo contradicen, manda el SQL).

## Requisitos

- JDK 25 (`JAVA_HOME` apuntando a él; `java -version` debe decir 25)
- Docker (para PostgreSQL)

## Levantar en desarrollo

```bash
# 1. PostgreSQL 18 (base/usuario/contraseña por defecto: ulibre/ulibre/ulibre, puerto 5432)
docker compose up -d

# 2. Aplicación con perfil dev (aplica migraciones + datos demo)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

API en http://localhost:8080 · Swagger UI en http://localhost:8080/swagger-ui.html
(botón **Authorize** → pegar el token del login).

> **Si el puerto 5432 ya está ocupado** (p. ej. un PostgreSQL instalado en Windows), usa otro:
> `DB_PORT=5433 docker compose up -d` y arranca la app con
> `DB_URL=jdbc:postgresql://localhost:5433/ulibre`.
>
> **Ojo con variables heredadas:** `docker compose` y el perfil dev leen `DB_USER`, `DB_PASSWORD`,
> `DB_NAME` y `SPRING_PROFILES_ACTIVE` del entorno. Si tu máquina ya los tiene definidos para otro
> proyecto, se usarán esos. Defínelos explícitamente en la terminal o en un archivo `.env` junto al
> `docker-compose.yml` (no se versiona).

### Reiniciar la base desde cero

```bash
docker compose down -v && docker compose up -d
```

Los datos demo programan la cirugía para **mañana a las 07:00** respecto al día en que se aplicó
la migración V100; para moverla a "mañana" otra vez, reinicia la base.

## Variables de entorno

| Variable         | Perfil default | Perfil dev (valor por defecto)                         |
|------------------|----------------|--------------------------------------------------------|
| `DB_URL`         | obligatoria    | `jdbc:postgresql://localhost:5432/ulibre`              |
| `DB_USER`        | obligatoria    | `ulibre`                                               |
| `DB_PASSWORD`    | obligatoria    | `ulibre`                                               |
| `JWT_SECRET`     | obligatoria, ≥ 32 bytes (si falta o es corta, la app no arranca) | secreto de desarrollo |
| `CORS_ORIGINS`   | `http://localhost:5173,http://localhost:3000` | igual                       |
| `DB_NAME`, `DB_PORT` | solo `docker-compose.yml` (por defecto `ulibre`, `5432`) |                    |

Perfiles:

- **default**: migraciones de `db/migration` (V1 esquema, V2 semilla estructural, V3 auth/alertas,
  V4 permisos del CRUD).
- **dev**: además `db/demo` (V100 datos demo con usuarios y la cirugía de bypass). Tiene
  `spring.flyway.out-of-order: true`: como los datos demo son V100, una migración nueva del esquema
  (V4, V5...) tiene número menor y, en una base que ya aplicó V100, Flyway la rechazaría.

`spring.jpa.hibernate.ddl-auto=validate`: Hibernate solo valida; el esquema lo maneja Flyway.

### Requisito sobre el usuario de la base

V1 ejecuta `ALTER DATABASE ... SET timezone = 'America/Bogota'` y `CREATE EXTENSION btree_gist`
(V100 además `pgcrypto`). El usuario con el que se conecta la app **debe ser dueño de la base**.
Con `docker compose` ya lo es (`POSTGRES_USER` es dueño de `POSTGRES_DB`). En un servidor gestionado,
crea la base con `CREATE DATABASE ulibre OWNER <usuario>;`.

Las migraciones V1/V2 son copia de los SQL de `docs/db` sin `BEGIN;`/`COMMIT;` (Flyway ya envuelve
cada migración en una transacción). **No se modifican**: cualquier cambio va en una migración nueva (V4__...).

### Hora

Las columnas son `timestamp` sin zona y guardan hora local de Colombia. La JVM fija
`America/Bogota` al arrancar y todo "ahora" del backend sale de un `Clock` en esa zona.
Las fechas de la API (`2026-10-02T07:00:00`) son hora local de Colombia.

## Usuarios demo (perfil dev)

Contraseña de todos: **`demo1234`**

| Correo                          | Rol del sistema       | En la cirugía demo                          |
|---------------------------------|-----------------------|---------------------------------------------|
| `admin@demo.local`              | ADMIN                 | —                                           |
| `enfermera.jefe@demo.local`     | ENFERMERA_JEFE        | programó y coordina                         |
| `alejandro.martinez@demo.local` | MEDICO                | cirujano                                    |
| `laura.rodriguez@demo.local`    | MEDICO                | anestesióloga                               |
| `maria.fernandez@demo.local`    | PERSONAL_QUIRURGICO   | instrumentadora (preparó el set cardiovascular) |
| `carlos.perez@demo.local`       | PERSONAL_QUIRURGICO   | auxiliar circulante, **operador del tablero** |
| `andres.gomez@demo.local`       | PERSONAL_QUIRURGICO   | perfusionista                               |
| `carlos.mendoza@demo.local`     | PACIENTE              | paciente (sin permisos globales)            |

La cirugía demo queda a propósito con **equipo incompleto** (faltan los 2 ayudantes quirúrgicos) e
**instrumental incompleto** (faltan `SET_VASCULAR` y `SEPARADOR_ESTERNAL`).

## Endpoints

Todos excepto el login y la documentación requieren `Authorization: Bearer <token>`.

| Método | Ruta                               | Permiso        | Descripción |
|--------|------------------------------------|----------------|-------------|
| POST   | `/api/auth/login`                  | público        | `{correo, contrasena}` → `{token, expiraEn, usuario{id, nombres, apellidos, correo, roles[], permisos[]}}`. Token de 8 h. |
| GET    | `/api/auth/me`                     | autenticado    | Usuario autenticado (roles y permisos frescos de la base). |
| GET    | `/api/cirugias?fecha=YYYY-MM-DD`   | `CIRUGIAS_VER` | Panel del día (por defecto hoy): paciente, procedimiento, quirófano, horario, estado, nº de alertas bloqueantes abiertas, operador del tablero, equipo completo. |
| GET    | `/api/cirugias/{id}/tablero`       | `TABLERO_VER`  | Todo el tablero: cirugía, paciente (edad calculada), procedimiento, preoperatorio, equipo, operador, checklist (fases → ítems → confirmaciones), instrumental, recuentos, alertas, hitos vigentes. 404 si no existe. |
| GET    | `/v3/api-docs`, `/swagger-ui.html` | público        | Contrato OpenAPI. |

### CRUD (iteración 2)

Detalle de cada endpoint en `docs/implementado/iteracion-2-crud.md` y en Swagger.

| Recurso | Ruta base | Ver / gestionar |
|---|---|---|
| Roles clínicos, especialidades, quirófanos | `/api/roles-clinicos`, `/api/especialidades`, `/api/quirofanos` | `CATALOGOS_VER` / `CATALOGOS_GESTIONAR` |
| Procedimientos (+ especialidades, roles, plantillas, sets e instrumentos predeterminados) | `/api/procedimientos` | `CATALOGOS_*` (plantillas: `PROTOCOLOS_GESTIONAR`; instrumental: `INSTRUMENTAL_GESTIONAR`) |
| Reglas de seguridad (solo configurar) | `/api/reglas-seguridad` | `CATALOGOS_VER` / `CATALOGOS_GESTIONAR` |
| Instrumentos y sets (+ composición) | `/api/instrumentos`, `/api/sets-instrumentales` | `INSTRUMENTAL_VER` / `INSTRUMENTAL_GESTIONAR` |
| Plantillas de checklist (+ fases, ítems, publicar, retirar, nueva versión) | `/api/plantillas-checklist` | `PROTOCOLOS_VER` / `PROTOCOLOS_GESTIONAR` |
| Usuarios, roles del sistema, permisos | `/api/usuarios`, `/api/roles-sistema`, `/api/permisos` | `USUARIOS_VER` / `USUARIOS_GESTIONAR` |
| Profesionales (+ roles clínicos, especialidades, disponibilidad, disponibles) | `/api/profesionales` | `PROFESIONALES_VER` / `PROFESIONALES_GESTIONAR` (disponibilidad: `DISPONIBILIDAD_GESTIONAR`) |
| Pacientes (+ alergias) | `/api/pacientes` | `PACIENTES_VER` / `PACIENTES_GESTIONAR` |
| Citas | `/api/citas` | `CITAS_VER` / `CITAS_GESTIONAR` |
| Solicitudes de cirugía (+ requerimientos, enviar, revisar, aprobar, rechazar, cancelar) | `/api/solicitudes-cirugia` | `SOLICITUDES_CIRUGIA_VER` / `SOLICITUDES_CIRUGIA_CREAR` (revisar/aprobar/rechazar: `CIRUGIAS_PROGRAMAR`) |
| Programación de cirugías (crear, reprogramar, estado) | `/api/cirugias` | `CIRUGIAS_VER` / `CIRUGIAS_PROGRAMAR` |
| Asignaciones y operador del tablero | `/api/cirugias/{id}/asignaciones`, `/api/cirugias/{id}/operador-tablero` | `CIRUGIAS_VER` / `PERSONAL_ASIGNAR` |
| Datos preoperatorios | `/api/cirugias/{id}/preoperatorio` | `CIRUGIAS_VER` / `PREPARACION_QUIRURGICA_GESTIONAR` |
| Incidentes | `/api/cirugias/{id}/incidentes` | `TABLERO_VER` / `INCIDENTES_GESTIONAR` |

Convenciones: `POST` → 201 + `Location`; `DELETE` en catálogos **desactiva** (`activo=false`), no
borra; los registros clínicos (cirugías, asignaciones, solicitudes, citas, incidentes) no tienen
`DELETE`: se cancelan. Las listas grandes se paginan con `pagina` (desde 0) y `tamano` (20 por
defecto, máximo 100; si se pide más se recorta) y responden
`{contenido, pagina, tamano, totalElementos, totalPaginas}`.

Ejemplo:

```bash
TOKEN=$(curl -s -H 'Content-Type: application/json' \
  -d '{"correo":"carlos.perez@demo.local","contrasena":"demo1234"}' \
  http://localhost:8080/api/auth/login | sed -E 's/.*"token":"([^"]+)".*/\1/')

curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/cirugias?fecha=$(date -d tomorrow +%F)"
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/cirugias/<id>/tablero
```

### Errores

Siempre `application/problem+json` (`type`, `title`, `status`, `detail`, `instance`):

| Situación                                                     | HTTP |
|---------------------------------------------------------------|------|
| Validación de la petición (`errores: [{campo, mensaje}]`)     | 400  |
| Sin token, token inválido/expirado, credenciales incorrectas  | 401  |
| Sin el permiso requerido                                      | 403  |
| Recurso inexistente                                           | 404  |
| Solape (SQLState `23P01`) o duplicado (`23505`)               | 409  |
| Regla de negocio de la base: CHECK o trigger (`23514`, `P0001`), FK (`23503`), NOT NULL (`23502`), texto demasiado largo (`22001`), número fuera de rango (`22003`) | 422 |
| Regla validada en Java (p. ej. transición de estado no permitida)                         | 422 |

En los errores de base, `detail` es el mensaje del trigger/constraint y se añaden `codigoSql` y,
si aplica, `restriccion` (nombre del constraint).

## Organización del código

Por funcionalidad (`com.hackaton.ulibre.*`):

- `auth` — login, JWT, seguridad, CORS
- `usuarios` — usuarios, roles del sistema y permisos
- `profesionales` — perfiles profesionales, disponibilidad, consulta de disponibles
- `pacientes`, `citas` — pacientes, alergias y citas
- `catalogos` — roles clínicos, especialidades, quirófanos (y vistas compartidas)
- `procedimientos` — procedimientos y su configuración predeterminada
- `protocolos` — plantillas de checklist (fases, ítems, versionado)
- `solicitudes` — solicitudes de cirugía y requerimientos de roles
- `cirugias` — panel, programación, asignaciones, operador del tablero, preoperatorio
- `incidentes` — novedades de una cirugía
- `tablero` — endpoint del tablero, recuentos, hitos
- `checklist`, `instrumental`, `alertas` — entidades y lecturas de cada parte (más el catálogo de
  instrumental y las reglas de seguridad)
- `comun` — zona horaria/reloj, manejo de errores, OpenAPI, paginación, base de catálogos

Entidades JPA delgadas (FKs como UUID, sin relaciones bidireccionales); las lecturas del panel y el
tablero son SQL nativo con `JdbcClient` hacia records, con un número fijo de consultas.
