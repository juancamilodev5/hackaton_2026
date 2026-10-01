# Iteración 2 — CRUD de las entidades

Fecha: 2026-10-01 · sobre la iteración 1 (`iteracion-1-base-backend.md`)

CRUD de toda entidad que se administra desde la aplicación: catálogos, instrumental, protocolos,
usuarios y roles, profesionales y disponibilidad, pacientes, citas, solicitudes, programación de
cirugías, asignaciones, datos preoperatorios e incidentes.

Las reglas de negocio siguen en la base (constraints y triggers) y no se reimplementan en Java. Java
valida solo lo que la base no cubre: transiciones de estado, existencia previa (para dar un 404
claro) y algunas protecciones administrativas. Esas validaciones lanzan `ReglaNegocioException`
y responden 422.

---

## 1. Qué no entra (iteración 3)

Las acciones de **ejecución del tablero** no son CRUD sino flujo, y quedan para la iteración 3:

- iniciar checklist (`fn_iniciar_checklist`);
- registrar y confirmar ítems, y cerrar fases;
- preparar instrumental (`fn_preparar_instrumental`) y marcarlo preparado;
- recuentos y sus confirmaciones;
- hitos y su anulación;
- reconocer, resolver y exceptuar alertas;
- motor de reglas, eventos de la cirugía, auditoría, panel de indicadores;
- filtro "el paciente solo ve lo suyo".

## 2. Cambios transversales

| Archivo | Cambio |
|---|---|
| `db/migration/V4__permisos_crud.sql` | Permisos nuevos `PACIENTES_VER/GESTIONAR`, `CITAS_VER/GESTIONAR`, `PROFESIONALES_VER/GESTIONAR`, `DISPONIBILIDAD_GESTIONAR`, `INCIDENTES_GESTIONAR` y su reparto por rol (ADMIN todo; MEDICO pacientes, citas, ver profesionales e incidentes; ENFERMERA_JEFE pacientes, citas, ver profesionales, disponibilidad e incidentes; PERSONAL_QUIRURGICO incidentes; AUDITOR_CALIDAD solo lectura). Idempotente. |
| `application.yml` (perfil dev) | `spring.flyway.out-of-order: true`. Los datos demo son V100: sin esto, una base que ya aplicó V100 rechazaría la V4. |
| `application.yml` | `logging.level.org.hibernate.orm.jdbc.error: error`. Hibernate registraba cada error SQL con sus líneas `Detail`, que pueden traer datos clínicos (§26 del maestro). Ya se traducen a 409/422, y los no mapeados los registra `ManejadorErrores`. |
| `comun/ManejadorErrores` | Responde 422 para `ReglaNegocioException`, `22001` (texto más largo que la columna) y `22003` (número fuera de rango). Los errores de `@PathVariable`/`@RequestParam` ahora responden 400 con la misma lista `errores` que el cuerpo. |
| `comun/RelojConfig` | El `Clock` avanza de a microsegundos (`Clock.tick`). En Windows el reloj da 100 ns y PostgreSQL guarda µs, así que la respuesta inmediata no coincidía con lo guardado. |
| `comun/Pagina`, `comun/Paginacion` | Paginación común `{contenido, pagina, tamano, totalElementos, totalPaginas}`. `pagina` negativa pasa a 0 y `tamano` se recorta a 1..100, sin responder 400. |
| `comun/Textos` | `limpiar` (recorta; vacío → null), `codigo` (mayúsculas), `correo` (minúsculas), `patronLike` (patrón "contiene" con `%`, `_` y `\` escapados). |
| `comun/UsuarioActual` | Id del usuario autenticado (claim `sub`) para las columnas "quién lo hizo". |
| `comun/catalogo/*` | Base de los catálogos simples (código, nombre, descripción, activo). `Catalogo` es un `@MappedSuperclass`; además hay `CatalogoRepository`, `CatalogoService` (listar, obtener, crear, actualizar, desactivar), `CatalogoRequest` y `CatalogoResponse`. La usan roles clínicos, especialidades, quirófanos, instrumentos y sets. |

## 3. Convenciones de la API

- `POST` de creación → 201 + `Location`. Las acciones (`/enviar`, `/publicar`...) → 200 con el
  recurso actualizado. `DELETE` → 204.
- **Catálogos**: `DELETE` desactiva (`activo=false`, regla 13), con un filtro `?activo=` opcional.
  Las configuraciones sin histórico sí se borran físicamente: roles, sets e instrumentos
  predeterminados de un procedimiento, composición de un set, disponibilidad, plantillas en
  BORRADOR, fases e ítems de un borrador.
- **Registros clínicos**: cirugías, asignaciones, solicitudes, citas e incidentes no tienen `DELETE`:
  se cancelan o cambian de estado. Las alergias se desactivan. Un paciente solo se borra si no
  tiene citas ni solicitudes.
- **Tablas puente** con PK compuesta: se escriben con `JdbcClient`. Asociar es un `PUT` idempotente
  (`ON CONFLICT DO NOTHING / DO UPDATE`); reemplazar un conjunto (`PUT .../roles`) borra lo que
  sobra e inserta lo que falta.
- **Lecturas con joins**: SQL nativo con `JdbcClient` hacia records, sin N+1. **Escrituras**: entidades
  JPA delgadas con `saveAndFlush`, para que los errores de triggers y constraints salgan dentro del
  request.
- **Transiciones de estado**: cada recurso las define en un solo lugar: `EstadoSolicitudCirugia`,
  `EstadoCita` y `TransicionesCirugia`.
- **Campos opcionales en los requests**: usan tipos envoltorio (`Boolean`, `Integer`). Jackson 3
  responde 400 si un primitivo falta en el JSON.
- **`jsonb`**: `JsonNode` en los requests y `@JsonRawValue String` en las respuestas.

## 4. Endpoints

### Catálogos — `CATALOGOS_VER` / `CATALOGOS_GESTIONAR`

| Método | Ruta | Descripción |
|---|---|---|
| GET, POST | `/api/roles-clinicos`, `/api/especialidades`, `/api/quirofanos` | Lista (`?activo=`) y alta |
| GET, PUT, DELETE | `…/{id}` | Detalle, edición, desactivación |
| GET | `/api/procedimientos?activo&especialidadId` | Lista |
| GET | `/api/procedimientos/{id}` | Detalle: especialidades, roles predeterminados, plantillas asociadas, sets e instrumentos predeterminados |
| POST, PUT, DELETE | `/api/procedimientos[/{id}]` | Alta, edición, desactivación (`duracionEstimadaMinutos` > 0) |
| PUT, DELETE | `/api/procedimientos/{id}/especialidades/{especialidadId}` | Habilitar o quitar especialidad. Quitarla si una solicitud la usa → 422 |
| GET, POST, PUT, DELETE | `/api/procedimientos/{id}/roles-predeterminados[/{rolPredId}]` | Roles sugeridos para el procedimiento |
| PUT, DELETE | `/api/procedimientos/{id}/plantillas-checklist/{plantillaId}` | `{esPredeterminada}`; al marcarla quita la anterior en la misma transacción. Permiso `PROTOCOLOS_GESTIONAR` |
| PUT, DELETE | `/api/procedimientos/{id}/sets-predeterminados/{setId}`, `…/instrumentos-predeterminados/{instrumentoId}` | `{cantidad, esRequerido, notas}`. Permiso `INSTRUMENTAL_GESTIONAR` |
| GET | `/api/reglas-seguridad[/{id}]` | Lista (`?activo=`) y detalle |
| PUT | `/api/reglas-seguridad/{id}` | Nombre, descripción, severidad, bloqueante, activo. Sin alta, sin baja y el código no se edita: la lógica de cada regla vive en el backend identificada por ese código |

### Instrumental — `INSTRUMENTAL_VER` / `INSTRUMENTAL_GESTIONAR`

| Método | Ruta | Descripción |
|---|---|---|
| GET, POST, PUT, DELETE | `/api/instrumentos[/{id}]` | Catálogo de instrumentos |
| GET, POST, PUT, DELETE | `/api/sets-instrumentales[/{id}]` | Catálogo de sets (el detalle incluye la composición) |
| GET | `/api/sets-instrumentales/{id}/instrumentos` | Composición |
| PUT, DELETE | `/api/sets-instrumentales/{id}/instrumentos/{instrumentoId}` | `{cantidad}` (upsert) o quitar |

### Protocolos de checklist — `PROTOCOLOS_VER` / `PROTOCOLOS_GESTIONAR`

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/plantillas-checklist?codigo&estado` | Lista, la versión más nueva primero |
| GET | `/api/plantillas-checklist/{id}` | Detalle: fases → ítems (rol responsable, `configValidacion`) |
| POST | `/api/plantillas-checklist` | Crea en BORRADOR con `version` = mayor versión del código + 1 |
| PUT, DELETE | `/api/plantillas-checklist/{id}` | Solo en BORRADOR (lo impone el trigger → 422). El borrado arrastra fases e ítems |
| POST | `…/{id}/publicar` | BORRADOR → PUBLICADA. Exige al menos una fase y que cada fase tenga ítems |
| POST | `…/{id}/retirar` | PUBLICADA → RETIRADA |
| POST | `…/{id}/nueva-version` | Copia fases e ítems a un BORRADOR v+1 (201) |
| POST, PUT, DELETE | `…/{id}/fases[/{faseId}]` | Fases (borrar una arrastra sus ítems) |
| POST, PUT, DELETE | `…/{id}/fases/{faseId}/items[/{itemId}]` | Ítems (`tipoRespuesta` en mayúsculas, `configValidacion` JSON) |

### Usuarios y roles del sistema — `USUARIOS_VER` / `USUARIOS_GESTIONAR`

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/usuarios?q&estado&pagina&tamano` | Paginada, con roles, `perfilProfesionalId`, `pacienteId`, `tieneContrasena` |
| GET, POST, PUT | `/api/usuarios[/{id}]` | Alta (contraseña opcional, mín. 8, BCrypt; roles por código) y edición de datos básicos |
| PUT | `/api/usuarios/{id}/estado`, `…/contrasena`, `…/roles` | Estado; contraseña (204); reemplazar roles por código |
| DELETE | `/api/usuarios/{id}` | Pasa a INACTIVO |
| GET, POST, PUT, DELETE | `/api/roles-sistema[/{id}]` | Roles con sus permisos (DELETE = `activo=false`) |
| PUT | `/api/roles-sistema/{id}/permisos` | Reemplaza los permisos (por código) |
| GET | `/api/permisos` | Catálogo de permisos |

Protecciones (422): nadie puede desactivarse ni suspenderse a sí mismo, ni quitarse el rol ADMIN. El
rol ADMIN no se desactiva, no cambia de código y no pierde permisos; sí se le pueden añadir. Nunca se
devuelve `hash_contrasena`.

### Profesionales — `PROFESIONALES_VER` / `PROFESIONALES_GESTIONAR`

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/profesionales?activo&rolClinicoId&especialidadId&q&pagina&tamano` | Paginada, con roles clínicos y especialidades |
| GET, POST, PUT, DELETE | `/api/profesionales[/{id}]` | Alta desde un `usuarioId` (un perfil por usuario → 409); DELETE = `activo=false` |
| PUT | `/api/profesionales/{id}/roles-clinicos`, `…/especialidades` | Reemplazan el conjunto `{ids}`. Quitar una especialidad que usa una solicitud → 422 |
| GET | `/api/profesionales/{id}/disponibilidad?desde&hasta` | Bloques que se solapan con el rango |
| POST, PUT, DELETE | `/api/profesionales/{id}/disponibilidad[/{dispId}]` | Permiso `DISPONIBILIDAD_GESTIONAR`; borrado físico |
| GET | `/api/profesionales/disponibles?desde&hasta&rolClinicoId[&especialidadId]` | Profesionales libres, con el mismo criterio que `fn_validar_asignacion`: activos, con el rol (y la especialidad), sin NO_DISPONIBLE ni otra cirugía vigente solapada. Indica `deTurno` y `marcadoDisponible`. Es una ayuda; quien valida al asignar sigue siendo la base |

### Pacientes y citas

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `/api/pacientes?q&pagina&tamano` | `PACIENTES_VER` | Paginada; `q` busca en nombre o documento; incluye edad calculada (regla 19) |
| GET | `/api/pacientes/{id}` | `PACIENTES_VER` | Detalle con alergias activas |
| POST, PUT | `/api/pacientes[/{id}]` | `PACIENTES_GESTIONAR` | Documento o usuario duplicado → 409; fecha de nacimiento futura → 400 |
| DELETE | `/api/pacientes/{id}` | `PACIENTES_GESTIONAR` | Solo sin citas ni solicitudes (si no, 422) |
| GET, POST, PUT, DELETE | `/api/pacientes/{id}/alergias[/{alergiaId}]` | `PACIENTES_*` | DELETE = `activo=false`; las copias del preoperatorio no cambian |
| GET | `/api/citas?pacienteId&medicoId&especialidadId&estado&desde&hasta&pagina&tamano` | `CITAS_VER` | Paginada |
| GET, POST, PUT | `/api/citas[/{id}]` | `CITAS_*` | Se crea SOLICITADA; se edita en SOLICITADA o CONFIRMADA. El médico debe estar activo y tener la especialidad (lo valida Java: la base no lo cubre) |
| PATCH | `/api/citas/{id}/estado` | `CITAS_GESTIONAR` | SOLICITADA → CONFIRMADA o CANCELADA; CONFIRMADA → COMPLETADA, CANCELADA o NO_ASISTIO |

### Solicitudes de cirugía

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `/api/solicitudes-cirugia?estado&pacienteId&medicoId&procedimientoId&pagina&tamano` | `SOLICITUDES_CIRUGIA_VER` | Paginada, más recientes primero |
| GET | `/api/solicitudes-cirugia/{id}` | `SOLICITUDES_CIRUGIA_VER` | Detalle con requerimientos (`asignacionesVigentes`) y `cirugiaId` |
| POST | `/api/solicitudes-cirugia` | `SOLICITUDES_CIRUGIA_CREAR` | BORRADOR. Médico implícito = perfil del usuario autenticado. Sin requerimientos en el body, se copian los roles predeterminados del procedimiento. Reglas 7 y 8 → 422 por las FKs compuestas |
| PUT | `/api/solicitudes-cirugia/{id}` | `SOLICITUDES_CIRUGIA_CREAR` | Datos clínicos, solo en BORRADOR o ENVIADA |
| POST, PUT, DELETE | `…/{id}/requerimientos[/{reqId}]` | `SOLICITUDES_CIRUGIA_CREAR` | Mientras no esté PROGRAMADA, RECHAZADA ni CANCELADA. No se baja la cantidad por debajo de las asignaciones vigentes |
| POST | `…/{id}/enviar` | `SOLICITUDES_CIRUGIA_CREAR` | BORRADOR → ENVIADA (exige requerimientos) |
| POST | `…/{id}/revisar`, `…/aprobar`, `…/rechazar` | `CIRUGIAS_PROGRAMAR` | ENVIADA → EN_REVISION; ENVIADA o EN_REVISION → APROBADA o RECHAZADA |
| POST | `…/{id}/cancelar` | `SOLICITUDES_CIRUGIA_CREAR` o `CIRUGIAS_PROGRAMAR` | → CANCELADA (salvo desde PROGRAMADA, RECHAZADA o CANCELADA) |

### Programación, equipo, preoperatorio e incidentes

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| POST | `/api/cirugias` | `CIRUGIAS_PROGRAMAR` | Programa una solicitud APROBADA (que pasa a PROGRAMADA). Sin `finProgramado` usa la duración estimada. Solape de quirófano → 409 |
| GET | `/api/cirugias/{id}` | `CIRUGIAS_VER` | Detalle de programación |
| PUT | `/api/cirugias/{id}` | `CIRUGIAS_PROGRAMAR` | Reprograma (solo PROGRAMADA, PREPARACION o LISTA). Si solapa al personal → 409 por el trigger |
| PATCH | `/api/cirugias/{id}/estado` | `CIRUGIAS_PROGRAMAR` | `{estado, motivo}`: avanza de a un paso. CANCELADA o SUSPENDIDA, con motivo obligatorio, antes de EN_CIRUGIA. EN_CIRUGIA con instrumental incompleto → 422 por el trigger |
| GET | `/api/cirugias/{id}/asignaciones` | `CIRUGIAS_VER` | Todas, incluidas las canceladas |
| POST | `/api/cirugias/{id}/asignaciones` | `PERSONAL_ASIGNAR` | Asigna; reactiva una rechazada o cancelada del mismo profesional. Rol, especialidad, cupo, solapes y NO_DISPONIBLE → 422/409 por el trigger |
| PATCH | `/api/cirugias/{id}/asignaciones/{aid}/estado` | `PERSONAL_ASIGNAR` | CONFIRMADA, RECHAZADA, CANCELADA (cancelar al operador le quita la marca) |
| PUT, DELETE | `/api/cirugias/{id}/operador-tablero` | `PERSONAL_ASIGNAR` | Designa (`{asignacionId}`) o quita el operador del tablero |
| GET, PUT | `/api/cirugias/{id}/preoperatorio` | `CIRUGIAS_VER` / `PREPARACION_QUIRURGICA_GESTIONAR` | Upsert; al crearlo copia las alergias activas del paciente (snapshot). Editar limpia la validación |
| POST | `…/preoperatorio/actualizar-alergias`, `…/preoperatorio/validar` | `PREPARACION_QUIRURGICA_GESTIONAR` | Volver a copiar alergias; validar (quién y cuándo) |
| GET | `/api/cirugias/{id}/incidentes` | `TABLERO_VER` | Más recientes primero |
| POST, PUT | `/api/cirugias/{id}/incidentes[/{iid}]` | `INCIDENTES_GESTIONAR` | Reportar (ABIERTO) y editar. RESUELTO registra quién y cuándo |

## 5. Verificación realizada

PostgreSQL 18 en Docker (puerto 5433), perfil dev:

- **Por módulo:** cada grupo de endpoints se probó contra la base real con:
  - caso feliz;
  - 400 de validación y 404;
  - 403 con un usuario sin el permiso;
  - errores de base traducidos: 409 por duplicados y solapes, 422 por CHECK, FK y triggers (p. ej. editar una plantilla PUBLICADA, asignar un rol que el profesional no tiene, quitar una especialidad en uso);
  - las reglas validadas en Java (transiciones inválidas, médico sin la especialidad, protecciones del ADMIN).
- **Prueba de punta a punta (31 verificaciones, todas OK):**
  - el médico crea el paciente, su alergia y la cita;
  - el médico crea la solicitud y la envía; la enfermera jefe la aprueba (el médico recibe 403 al intentarlo);
  - la enfermera programa la cirugía y asigna el equipo (422 al asignar un rol que el profesional no tiene) y designa a Carlos como operador;
  - preoperatorio con copia de alergias y validación;
  - el tablero de la iteración 1 refleja todo: operador, alergias copiadas, quién validó;
  - un incidente queda registrado y la cirugía aparece en el panel del día;
  - al cancelar, el motivo es obligatorio.
- **Build:** `mvn clean test` → BUILD SUCCESS; Hibernate `validate` sin errores.
- **Swagger:** 77 rutas y 136 operaciones.
- **Datos de prueba:** borrados; la base quedó con los datos demo intactos.

**Sin probar en ejecución** (no había datos para provocarlas sin alterar la demo; el código existe):

- bajar la cantidad de un requerimiento por debajo de las asignaciones vigentes;
- el 422 del trigger de instrumental incompleto al pasar a EN_CIRUGIA;
- médico o especialidad inactivos en una cita.

## 6. Notas para probar desde Git Bash en Windows

`curl -d '{"nombre":"Urología"}'` envía el texto en CP1252 y la API responde 400 «Failed to read
request». No es un error de la API: hay que escribir el cuerpo en un archivo UTF-8 y enviarlo con
`--data-binary @archivo`, o usar Swagger UI.
