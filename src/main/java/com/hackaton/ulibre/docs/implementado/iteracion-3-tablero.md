# Iteración 3 — Ejecución del tablero, alertas y trazabilidad

Fecha: 2026-10-01 · sobre la iteración 2 (`iteracion-2-crud.md`)

Flujo del tablero de seguridad quirúrgica: checklist (iniciar, responder, confirmar y cerrar fases),
preparación del instrumental, recuentos, hitos, gestión de alertas y motor de reglas. Incluye
además el timeline de eventos de la cirugía, la auditoría administrativa, los indicadores de
calidad y el portal del paciente ("el paciente solo ve lo suyo").

Se mantiene el criterio de las iteraciones anteriores. La base garantiza lo esencial con triggers y
constraints:

- el operador del tablero;
- el rol clínico de quien confirma y que su asignación esté vigente;
- un hito vigente por tipo;
- que una fase no se cierre con alertas bloqueantes;
- que la cirugía no empiece sin el instrumental completo;
- las tablas de solo agregar.

Java añade el flujo que la base no cubre (por ejemplo, las fases en orden o la forma de la
respuesta según su tipo) y responde 422 con `ReglaNegocioException`.

---

## 1. Quién registra y quién confirma

| Concepto | Dónde vive | Cómo se comprueba |
|---|---|---|
| **Rol del sistema** (ADMIN, MEDICO, ENFERMERA_JEFE, PERSONAL_QUIRURGICO…) | `usuarios` → `roles_sistema` → permisos | `@PreAuthorize` en el controlador. Solo habilita la función. |
| **Participación en la cirugía** (persona y rol clínico en ESA cirugía) | `asignaciones_personal_cirugia` (ASIGNADA/CONFIRMADA) | `cirugias/ParticipacionCirugia.asignacionVigente`. Todo lo que se registra se firma con la asignación. |
| **Operador del tablero** (no es un rol clínico) | `asignaciones_personal_cirugia.es_operador_tablero` | `ParticipacionCirugia.exigirOperador`. Tener `TABLERO_OPERAR` no basta: hay que ser el operador vigente de esa cirugía. |
| **Confirmación clínica** | `confirmaciones_item_checklist`, `confirmaciones_recuento` | La hace la persona en su rol. La registra ella misma o el operador en su nombre. La base valida el rol que exige el ítem y la vigencia de la asignación. |

Ejemplo (verificado en `TableroTest`):

- El operador Carlos Pérez registra la respuesta de `IDENTIDAD_PACIENTE`, que queda como `registradoPor`.
- La Dra. Laura Rodríguez confirma el ítem como ANESTESIOLOGO, que queda como `confirmaciones[].confirmadoPor`.
- El evento `ITEM_CONFIRMADO` guarda a quien confirmó como actor y a quien registró en `datos.registradoPor`.

## 2. Cambios transversales

| Archivo | Cambio |
|---|---|
| `cirugias/ParticipacionCirugia` | Estado de la cirugía (404), asignación vigente del usuario, `exigirAsignado` y `exigirOperador`. |
| `eventos/EventosCirugia`, `eventos/TipoEvento` | Timeline operacional (`eventos_cirugia`, solo agregar). Se escribe en la misma transacción que la acción. `TipoEvento` es el único catálogo de códigos, porque en el esquema la columna es `varchar` libre. |
| `auditoria/Auditoria`, `auditoria/AccionAuditoria` | Auditoría administrativa (`registros_auditoria`) con los valores antes y después. Se guardan los DTOs de respuesta, nunca la entidad, para que no se filtre `hash_contrasena`. |
| Servicios del CRUD (19) | Registran auditoría en altas, ediciones, desactivaciones, borrados y cambios de estado: catálogos, procedimientos, sets, plantillas, usuarios, roles, profesionales, disponibilidad, pacientes, alergias, citas, solicitudes y reglas. Los que tocan una cirugía (programación, asignaciones, preoperatorio, incidentes) registran además eventos y reevalúan las reglas. |
| `cirugias/ProgramacionCirugiasService` | Pasar a `EN_CIRUGIA` evalúa antes las reglas (momento INICIO_CIRUGIA). Pasar a `COMPLETADA` exige el checklist completado (regla 14 del maestro; la base no lo cubre). Los cambios de estado y las reprogramaciones quedan en el timeline. |
| `solicitudes/SolicitudesService` | El médico puede ajustar los roles requeridos con la solicitud PROGRAMADA mientras la cirugía no haya empezado. |
| `pom.xml` y tests | Testcontainers (PostgreSQL 18 desechable). Infraestructura de tests de API: `ApiTest`, `ClienteApi`, `PostgresDePrueba`. |

### Permisos: no hace falta migración nueva

Todos los permisos que usa la iteración 3 ya existen en la semilla V2 y están repartidos por rol:

- `TABLERO_VER`, `TABLERO_OPERAR`;
- `PREPARACION_QUIRURGICA_GESTIONAR`;
- `ALERTAS_GESTIONAR`;
- `CALIDAD_VER`, `AUDITORIA_VER`.

Se comprobó cruzando cada `hasAuthority` del código con los `INSERT INTO permisos` de V2 a V4: no
falta ninguno. El rol PACIENTE sigue sin permisos globales, como manda V2. El portal se protege
filtrando por identidad, no con un permiso.

**Corrección hecha al cerrar la iteración:** los dos endpoints de confirmación (ítem de checklist y
etapa de recuento) exigían `TABLERO_OPERAR`. Ese permiso no lo tiene el rol MEDICO, así que la
anestesióloga recibía 403 al confirmar su propio ítem, aunque el servicio estaba hecho para
permitirlo ("la registra el operador o la propia persona"). Ahora exigen `TABLERO_VER`. La
seguridad no se pierde, porque el servicio exige:

- una asignación vigente en la cirugía;
- que solo el operador registre en nombre de otra persona;
- el rol clínico que pide el ítem, que valida la base.

Con esto no hizo falta dar `TABLERO_OPERAR` a los médicos, que habría significado que operan el
tablero.

## 3. Endpoints

### Checklist — `/api/cirugias/{id}/checklist`

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `…/checklist` | `TABLERO_VER` | Fases → ítems → confirmaciones. 404 si no hay checklist |
| POST | `…/checklist` | `TABLERO_OPERAR` + operador | `{plantillaId?}`. Copia la plantilla con `fn_iniciar_checklist` si aún no existe y la inicia: checklist EN_PROGRESO y primera fase abierta. 201. Si ya estaba iniciado → 422 |
| PUT | `…/checklist/items/{itemId}/respuesta` | `TABLERO_OPERAR` + operador | `{estado, respuesta, notas}`. Solo ítems de la fase actual. La forma de `respuesta` depende de `tipoRespuesta` (`ValidadorRespuesta`, con `config_validacion` del ítem de plantilla). Se puede corregir mientras la fase esté abierta |
| POST | `…/checklist/items/{itemId}/confirmaciones` | `TABLERO_VER` + asignación vigente | `{asignacionId?, resultado, notas}`. Sin `asignacionId` confirma el propio usuario; con otra persona, solo el operador. El ítem debe estar respondido y su fase sin cerrar. 201 |
| POST | `…/checklist/fases/{faseId}/cerrar` | `TABLERO_OPERAR` + operador | `{notas?}`. Solo la fase actual (en orden). Evalúa las reglas antes de escribir y luego decide `fn_validar_cierre_fase` (alertas bloqueantes → 422). Abre la siguiente fase o completa el checklist, y registra el hito automático (`PREANESTESIA_COMPLETADA`, `PREINCISION_COMPLETADA`, `SALIDA_COMPLETADA`) |

Los ítems obligatorios pendientes no se comprueban en Java. Los detecta la regla
`FASE_OBLIGATORIA_INCOMPLETA` y, si es bloqueante, el trigger rechaza el cierre.

### Instrumental de la cirugía — `/api/cirugias/{id}/instrumental`

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `…/instrumental` | `CIRUGIAS_VER` o `TABLERO_VER` | Sets e instrumentos de la cirugía, `instrumentalCompleto` y faltantes (mismo criterio que `fn_exigir_instrumental_completo`) |
| POST | `…/instrumental/preparar` | `PREPARACION_QUIRURGICA_GESTIONAR` | Snapshot del instrumental predeterminado del procedimiento (`fn_preparar_instrumental`). Los cambios posteriores del catálogo no alteran la cirugía |
| PUT | `…/instrumental/sets/{setId}` | `PREPARACION_QUIRURGICA_GESTIONAR` + asignación vigente | `{cantidadPreparada, notas}`. Registra quién preparó (su asignación) y cuándo. 0 limpia la preparación |
| PUT | `…/instrumental/instrumentos/{instrumentoId}` | ídem | Ídem para un instrumento directo |

### Recuentos — `/api/cirugias/{id}/recuentos`

El recuento acompaña todo el proceso:

1. conteo inicial;
2. material agregado durante la cirugía;
3. conteo final;
4. confirmación de cada etapa.

La cantidad esperada (inicial + agregada) y el estado (`PENDIENTE`/`CUADRA`/`DISCREPANCIA`) los
calcula la vista `v_recuentos_cirugia`. Una discrepancia genera la alerta
`RECUENTO_INCONSISTENTE` (CRITICA, bloqueante).

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `…/recuentos` | `TABLERO_VER` | Con esperada, estado y confirmaciones de las dos etapas (INICIAL y FINAL) |
| POST | `…/recuentos` | `TABLERO_OPERAR` + operador | `{tipoRecuento, descripcion?, instrumentoCirugiaId?, cantidadInicial, notas?}`. 201 |
| POST | `…/recuentos/{rid}/agregados` | `TABLERO_OPERAR` + operador | `{cantidad ≥ 1, notas?}`. Solo antes del conteo final |
| PUT | `…/recuentos/{rid}/final` | `TABLERO_OPERAR` + operador | `{cantidadFinal, notas?}`. Se puede corregir hasta que alguien confirme la etapa FINAL. Reevalúa las reglas |
| POST | `…/recuentos/{rid}/confirmaciones` | `TABLERO_VER` + asignación vigente | `{etapa, asignacionId?, notas?}`. Igual que en el checklist: confirma la persona y solo el operador registra en nombre de otra |

### Hitos — `/api/cirugias/{id}/hitos`

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `…/hitos?incluirAnulados=false` | `TABLERO_VER` | En orden cronológico. Por defecto solo los vigentes |
| POST | `…/hitos` | `TABLERO_OPERAR` + operador | `{tipoHito, ocurridoEn?, notas?, corrigeHitoId?}`. 201. Hora futura (más de 1 min) → 422. Un hito vigente por tipo → 409. `CIRUGIA_INICIADA` evalúa antes las reglas de inicio, y sin instrumental completo → 422 (trigger) |
| POST | `…/hitos/{hid}/anular` | `TABLERO_OPERAR` + operador | `{motivo}`. No se borra ni se edita. Anularlo dos veces → 422 (`fn_proteger_hito`). La corrección se registra después con `corrigeHitoId` |

Los hitos no cambian el estado de la cirugía. El estado se cambia con `PATCH /api/cirugias/{id}/estado`.

### Alertas y motor de reglas — `/api/cirugias/{id}`

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `…/alertas` | `TABLERO_VER` | Primero las vigentes, después por severidad y fecha |
| POST | `…/reglas/evaluar` | `TABLERO_OPERAR` o `ALERTAS_GESTIONAR` | Reevalúa: genera las alertas nuevas y resuelve solas las que ya no aplican. `{alertasGeneradas, alertasResueltas, alertas}` |
| POST | `…/alertas/{aid}/reconocer` | `ALERTAS_GESTIONAR` | Solo una alerta ABIERTA. Queda RECONOCIDA (quién y cuándo) y sigue vigente |
| POST | `…/alertas/{aid}/resolver` | `ALERTAS_GESTIONAR` | `{notas}`. 422 si la condición de la regla sigue presente |
| POST | `…/alertas/{aid}/descartar` | `ALERTAS_GESTIONAR` | `{notas}`. Solo las no bloqueantes |
| POST | `…/alertas/{aid}/excepcion` | `ALERTAS_GESTIONAR` | `{motivo}`. Autoriza continuar frente a una alerta bloqueante (quién, cuándo y motivo). La alerta sigue vigente y visible, pero los triggers dejan de contarla |

`alertas/MotorReglas` tiene una función por cada código sembrado en V2, sin reglas inventadas:

- `DATOS_OBLIGATORIOS_INCOMPLETOS`
- `SITIO_QUIRURGICO_NO_CONFIRMADO`
- `ALERGIA_NO_CONFIRMADA`
- `ANTIBIOTICO_PENDIENTE`
- `EQUIPO_QUIRURGICO_INCOMPLETO`
- `OPERADOR_TABLERO_NO_ASIGNADO`
- `INSTRUMENTAL_REQUERIDO_INCOMPLETO`
- `RECUENTO_INCONSISTENTE`
- `FASE_OBLIGATORIA_INCOMPLETA`

`reglas_seguridad` solo configura cada regla: si está activa, su severidad y si bloquea.

Cada evaluación da hallazgo, ok o no aplica, según el momento (`Momento`: EVALUACION, CIERRE_FASE,
INICIO_CIRUGIA). Los datos que leen las reglas los reúne `ContextoReglas`. El motor tiene dos
formas de ejecutarse:

- **`evaluar`** corre en una transacción propia (`REQUIRES_NEW`) y se llama ANTES de escribir.
  Así las alertas quedan guardadas aunque el trigger rechace después la operación.
- **`evaluarAlConfirmar`** corre después del commit de la acción, para reflejar su efecto.

### Eventos, auditoría, indicadores y trazabilidad

| Método | Ruta | Permiso | Descripción |
|---|---|---|---|
| GET | `/api/cirugias/{id}/eventos` | `TABLERO_VER` o `AUDITORIA_VER` | Timeline cronológico: tipo, actor (usuario y, si participa, su asignación con el rol clínico), entidad relacionada, `datos` (jsonb) |
| GET | `/api/cirugias/{id}/trazabilidad` | `CALIDAD_VER`, `AUDITORIA_VER` o `TABLERO_VER` | Hitos vigentes, tiempos medidos entre hitos (`Tramo`), conteo de alertas e incidentes y timeline. Lectura REPEATABLE READ |
| GET | `/api/indicadores?desde&hasta` | `CALIDAD_VER` | Cirugías del rango, por defecto los últimos 30 días: por estado, tiempos por tramo, checklist, alertas, recuentos e incidentes. Solo datos reales. `desde > hasta` → 422 |
| GET | `/api/auditoria?tipoEntidad&entidadId&usuarioId&accion&desde&hasta&pagina&tamano` | `AUDITORIA_VER` | Auditoría administrativa paginada, más recientes primero, con `valoresAnteriores`/`valoresNuevos` |

Hay dos registros distintos:

- **`eventos_cirugia`** guarda lo que ocurrió en el flujo quirúrgico.
- **`registros_auditoria`** guarda los cambios administrativos de entidades.

### Portal del paciente — `/api/mi` (autenticado)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/mi/paciente` | Mis datos, edad calculada y alergias activas |
| GET | `/api/mi/citas` | Mis citas |
| GET | `/api/mi/solicitudes` | Mis solicitudes de cirugía, sin `notas_medicas` |
| GET | `/api/mi/cirugias`, `/api/mi/cirugias/{id}` | Mis cirugías. Si la cirugía no existe o es de otro paciente → 404, sin revelar que existe |

Cómo se aísla a cada paciente:

- La identidad sale del token: `pacientes.usuario_id` = `sub`, que es único por
  `uq_pacientes_usuario`.
- Todas las consultas filtran por ese paciente en SQL, nunca por un id que mande el cliente.
- Un usuario sin registro de paciente recibe 404.
- El rol PACIENTE no tiene permisos globales, así que el resto de la API le responde 403.

## 4. Verificación realizada

PostgreSQL 18 con Testcontainers, perfil dev (migraciones reales V1 a V4 y datos demo V100):

- **`./mvnw compile`** → OK con JDK 25.
- **`./mvnw test`** → 7 tests, 0 fallos (ver el README para el resultado final):
  - `UlibreApplicationTests`: arranque, Flyway y Hibernate `validate`;
  - `CrudTest` (3): catálogo con auditoría, traducción de errores y plantilla publicada;
  - `TableroTest` (3, nuevo):
    - `flujoDelTablero`, sobre la cirugía demo:
      - solo el operador inicia el checklist; ADMIN recibe 422 aunque tenga `TABLERO_OPERAR`;
      - confirmar antes de responder → 422; respuesta con forma inválida → 422;
      - la anestesióloga confirma su propio ítem y queda con su rol ANESTESIOLOGO;
      - el operador registra la confirmación en su nombre; la instrumentadora no puede, y quien no participa tampoco;
      - recuento 10 + 5 con final 14 → DISCREPANCIA, confirmación de las dos etapas y final ya confirmado sin poder corregirse;
      - la alerta `RECUENTO_INCONSISTENTE` bloqueante: 403 sin `ALERTAS_GESTIONAR`; se reconoce; resolver o descartar mientras siga la discrepancia → 422; la excepción se autoriza y la alerta sigue vigente;
      - hitos: solo el operador, duplicado → 409, anular dos veces → 422, corrección con `corrigeHitoId`, listado con y sin anulados;
      - `CIRUGIA_INICIADA` sin instrumental → 422;
      - el timeline contiene cada tipo de evento esperado.
    - `elPacienteSoloVeLoSuyo`: ve sus datos y su cirugía; 404 para una cirugía ajena; 403 en el panel y el tablero; 404 en `/api/mi/*` para un usuario sin registro de paciente.
    - `indicadoresYTrazabilidad`: 200, rango invertido → 422, y 403 sin `CALIDAD_VER`/`AUDITORIA_VER`.

**Sin test automático** (el código existe; la demo no tiene datos para provocarlo sin otra cirugía):

- cerrar una fase completa hasta completar el checklist y pasar la cirugía a COMPLETADA;
- preparar el instrumental hasta dejarlo completo;
- el cierre de fase rechazado por `FASE_OBLIGATORIA_INCOMPLETA`;
- las reglas de antibiótico, alergia y sitio quirúrgico.

## 5. Limitaciones y pendientes

- `GET …/eventos` y `…/trazabilidad` no se paginan. Para una cirugía, el volumen es acotado.
- `TableroTest.flujoDelTablero` modifica la cirugía demo dentro del contenedor de test. Los otros tests no dependen de ese estado, pero un test futuro que lo haga debería crear su propia cirugía.
- El `JAVA_HOME` de la máquina de desarrollo puede apuntar a JDK 21. El build exige JDK 25 (`release 25`).
