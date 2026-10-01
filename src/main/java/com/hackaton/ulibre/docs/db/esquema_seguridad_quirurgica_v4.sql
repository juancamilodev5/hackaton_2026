-- =====================================================================
-- Plataforma de Gestión y Seguridad Quirúrgica — Esquema V4
--
-- CAMBIOS RESPECTO A V3
--   - Catálogo de instrumental: instrumentos, sets, composición de sets y
--     sets/instrumentos predeterminados por procedimiento.
--   - Snapshot de instrumental por cirugía (sets_instrumentales_cirugia,
--     instrumentos_cirugia) + función fn_preparar_instrumental.
--   - cirugia_id en fases, ítems y confirmaciones: la pertenencia a la misma
--     cirugía se garantiza con FKs compuestas.
--   - Recuento opcionalmente ligado a un instrumento del snapshot.
--   - Hitos nuevos: INGRESO_QUIROFANO, TABLERO_INICIADO.
--   - Reglas nuevas en base de datos:
--       * quien confirma un ítem debe cubrir el rol clínico requerido
--       * no se inicia el tablero sin operador
--       * no se inicia la cirugía con instrumental obligatorio incompleto
--         (salvo excepción autorizada en una alerta)
--       * no se completa una fase con alertas bloqueantes abiertas
--         (salvo excepción autorizada)
--       * los registros clínicos de una cirugía no se borran
--
-- CAMBIOS DE V3 RESPECTO A V2
--   - Operador del tablero por cirugía (asignaciones.es_operador_tablero),
--     como máximo uno vigente por cirugía.
--   - Se separa quién REGISTRA en el tablero de quién CONFIRMA clínicamente:
--       items_checklist_cirugia.registrado_por_asignacion_id
--       + nueva tabla confirmaciones_item_checklist
--       recuentos_cirugia.registrado_*_por_asignacion_id
--       + nueva tabla confirmaciones_recuento
--   - Quien registra o confirma debe estar asignado a ESA cirugía (FK o trigger).
--   - Nueva función fn_iniciar_checklist: copia la plantilla a la cirugía.
-- Motor: PostgreSQL 18  (usa uuidv7() nativo de PG18)
--
-- Contenido:
--   0. Extensiones
--   1. Tipos ENUM
--   2. Tablas (en orden de dependencias) con CHECK y FKs
--   3. Índices
--   4. Funciones y triggers de reglas de negocio
--        - actualizado_en automático
--        - asignaciones: rol clínico, especialidad, cupo, solapes (reglas 1, 4, 9, 17)
--        - cambio de horario de una cirugía re-valida al personal
--        - plantillas publicadas inmutables (regla 10)
--        - eventos y auditoría solo-agregar (regla 12)
--        - hitos: solo se puede anular, nunca sobrescribir
--        - registro/confirmación solo por personal vigente de la misma cirugía
--        - confirmación solo por quien cubre el rol clínico del ítem
--        - tablero sin operador / cirugía sin instrumental / fase con alertas
--        - registros clínicos sin borrado físico
--        - fn_iniciar_checklist y fn_preparar_instrumental (snapshots)
--
-- Convenciones:
--   - Fechas SIN zona horaria (timestamp). Convención: todas las horas se
--     guardan en hora local de Colombia. La base queda configurada en
--     America/Bogota para que localtimestamp devuelva esa hora.
--   - Las FKs no usan CASCADE: el histórico clínico no se borra (regla 22).
--   - Ejecutar con nivel de aislamiento READ COMMITTED (el predeterminado):
--     los triggers de solape dependen de ello.
-- =====================================================================

BEGIN;

-- =====================================================================
-- 0. EXTENSIONES
-- =====================================================================

-- Necesaria para combinar "=" sobre uuid con "&&" sobre rangos de tiempo
-- en la restricción de exclusión de quirófanos.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Las columnas son timestamp (sin zona horaria) y los valores por defecto
-- usan localtimestamp, que depende de la zona de la sesión. Se fija la de
-- Colombia para esta sesión y para todas las futuras conexiones a esta base,
-- así un servidor en la nube (normalmente en UTC) no guarda horas corridas.
-- Requiere ser dueño de la base de datos.
SET TIME ZONE 'America/Bogota';
DO $$
BEGIN
EXECUTE format('ALTER DATABASE %I SET timezone = %L', current_database(), 'America/Bogota');
END;
$$;


-- =====================================================================
-- 1. TIPOS ENUM
-- =====================================================================

CREATE TYPE estado_usuario AS ENUM ('ACTIVO', 'INACTIVO', 'SUSPENDIDO');

CREATE TYPE estado_cita AS ENUM ('SOLICITADA', 'CONFIRMADA', 'COMPLETADA', 'CANCELADA', 'NO_ASISTIO');

CREATE TYPE estado_solicitud_cirugia AS ENUM (
  'BORRADOR', 'ENVIADA', 'EN_REVISION', 'APROBADA', 'PROGRAMADA', 'RECHAZADA', 'CANCELADA'
);

CREATE TYPE estado_cirugia AS ENUM (
  'PROGRAMADA', 'PREPARACION', 'LISTA', 'EN_QUIROFANO', 'ANESTESIA', 'EN_CIRUGIA',
  'CIERRE', 'RECUPERACION', 'COMPLETADA', 'CANCELADA', 'SUSPENDIDA'
);

CREATE TYPE estado_asignacion AS ENUM ('ASIGNADA', 'CONFIRMADA', 'RECHAZADA', 'CANCELADA');

CREATE TYPE tipo_disponibilidad AS ENUM ('DISPONIBLE', 'NO_DISPONIBLE', 'DE_TURNO');

CREATE TYPE estado_checklist AS ENUM ('PENDIENTE', 'EN_PROGRESO', 'COMPLETADO');

CREATE TYPE estado_fase_checklist AS ENUM ('PENDIENTE', 'EN_PROGRESO', 'COMPLETADA');

CREATE TYPE estado_item_checklist AS ENUM ('PENDIENTE', 'COMPLETADO', 'ADVERTENCIA', 'FALLIDO', 'NO_APLICA');

CREATE TYPE severidad_alerta AS ENUM ('INFORMATIVA', 'BAJA', 'MEDIA', 'ALTA', 'CRITICA');

CREATE TYPE estado_alerta AS ENUM ('ABIERTA', 'RECONOCIDA', 'RESUELTA', 'DESCARTADA');

CREATE TYPE estado_plantilla AS ENUM ('BORRADOR', 'PUBLICADA', 'RETIRADA');

CREATE TYPE estado_incidente AS ENUM ('ABIERTO', 'EN_PROGRESO', 'RESUELTO', 'CANCELADO');

CREATE TYPE lateralidad AS ENUM ('IZQUIERDA', 'DERECHA', 'BILATERAL', 'NO_APLICA');

CREATE TYPE estado_reserva_sangre AS ENUM ('NO_REQUERIDA', 'PENDIENTE', 'RESERVADA', 'NO_DISPONIBLE');

CREATE TYPE tipo_hito AS ENUM (
  'LLEGADA_PACIENTE', 'INGRESO_QUIROFANO', 'TABLERO_INICIADO', 'PREANESTESIA_COMPLETADA', 'ANESTESIA_INICIADA', 'PREINCISION_COMPLETADA',
  'CIRUGIA_INICIADA', 'CIRUGIA_FINALIZADA', 'SALIDA_COMPLETADA', 'TRASLADO_A_RECUPERACION'
);

CREATE TYPE tipo_recuento AS ENUM ('GASAS', 'COMPRESAS', 'AGUJAS', 'INSTRUMENTAL', 'OTRO');

CREATE TYPE etapa_recuento AS ENUM ('INICIAL', 'FINAL');

CREATE TYPE resultado_confirmacion AS ENUM ('CONFIRMADO', 'NO_CONFIRMADO');


-- =====================================================================
-- 2. TABLAS
-- =====================================================================

-- ---------------------------------------------------------------------
-- IDENTIDAD / AUTORIZACIÓN
-- ---------------------------------------------------------------------

CREATE TABLE usuarios (
                          id              uuid PRIMARY KEY DEFAULT uuidv7(),
                          nombres         varchar(100) NOT NULL,
                          apellidos       varchar(100) NOT NULL,
                          correo          varchar(180) NOT NULL,
                          telefono        varchar(50),
                          estado          estado_usuario NOT NULL DEFAULT 'ACTIVO',
                          creado_en       timestamp NOT NULL DEFAULT localtimestamp,
                          actualizado_en  timestamp NOT NULL DEFAULT localtimestamp,

                          CONSTRAINT uq_usuarios_correo UNIQUE (correo)
);

CREATE TABLE roles_sistema (
                               id           uuid PRIMARY KEY DEFAULT uuidv7(),
                               codigo       varchar(60)  NOT NULL,
                               nombre       varchar(100) NOT NULL,
                               descripcion  varchar(250),
                               activo       boolean NOT NULL DEFAULT true,

                               CONSTRAINT uq_roles_sistema_codigo UNIQUE (codigo)
);

CREATE TABLE permisos (
                          id           uuid PRIMARY KEY DEFAULT uuidv7(),
                          codigo       varchar(100) NOT NULL,
                          nombre       varchar(150) NOT NULL,
                          descripcion  varchar(250),

                          CONSTRAINT uq_permisos_codigo UNIQUE (codigo)
);

CREATE TABLE usuario_roles_sistema (
                                       usuario_id      uuid NOT NULL REFERENCES usuarios (id),
                                       rol_sistema_id  uuid NOT NULL REFERENCES roles_sistema (id),
                                       asignado_en     timestamp NOT NULL DEFAULT localtimestamp,

                                       PRIMARY KEY (usuario_id, rol_sistema_id)
);

CREATE TABLE rol_sistema_permisos (
                                      rol_sistema_id  uuid NOT NULL REFERENCES roles_sistema (id),
                                      permiso_id      uuid NOT NULL REFERENCES permisos (id),

                                      PRIMARY KEY (rol_sistema_id, permiso_id)
);

-- ---------------------------------------------------------------------
-- PACIENTES
-- ---------------------------------------------------------------------

CREATE TABLE pacientes (
                           id                            uuid PRIMARY KEY DEFAULT uuidv7(),
                           usuario_id                    uuid REFERENCES usuarios (id),   -- NULL: paciente sin cuenta
                           nombres                       varchar(100) NOT NULL,
                           apellidos                     varchar(100) NOT NULL,
                           tipo_documento                varchar(30)  NOT NULL,
                           numero_documento              varchar(50)  NOT NULL,
                           fecha_nacimiento              date,                            -- la edad se calcula, no se guarda
                           telefono                      varchar(50),
                           correo                        varchar(180),
                           contacto_emergencia_nombre    varchar(150),
                           contacto_emergencia_telefono  varchar(50),
                           creado_en                     timestamp NOT NULL DEFAULT localtimestamp,
                           actualizado_en                timestamp NOT NULL DEFAULT localtimestamp,

                           CONSTRAINT uq_pacientes_usuario   UNIQUE (usuario_id),
                           CONSTRAINT uq_pacientes_documento UNIQUE (tipo_documento, numero_documento)
);

CREATE TABLE alergias_paciente (
                                   id              uuid PRIMARY KEY DEFAULT uuidv7(),
                                   paciente_id     uuid NOT NULL REFERENCES pacientes (id),
                                   sustancia       varchar(150) NOT NULL,
                                   reaccion        varchar(250),
                                   severidad       varchar(50),
                                   notas           text,
                                   activo          boolean NOT NULL DEFAULT true,
                                   creado_en       timestamp NOT NULL DEFAULT localtimestamp,
                                   actualizado_en  timestamp NOT NULL DEFAULT localtimestamp
);

-- ---------------------------------------------------------------------
-- PROFESIONALES
-- ---------------------------------------------------------------------

CREATE TABLE perfiles_profesionales (
                                        id                    uuid PRIMARY KEY DEFAULT uuidv7(),
                                        usuario_id            uuid NOT NULL REFERENCES usuarios (id),
                                        licencia_profesional  varchar(100),
                                        numero_profesional    varchar(100),
                                        activo                boolean NOT NULL DEFAULT true,
                                        creado_en             timestamp NOT NULL DEFAULT localtimestamp,
                                        actualizado_en        timestamp NOT NULL DEFAULT localtimestamp,

                                        CONSTRAINT uq_perfiles_profesionales_usuario UNIQUE (usuario_id)
);

CREATE TABLE roles_clinicos (
                                id              uuid PRIMARY KEY DEFAULT uuidv7(),
                                codigo          varchar(60)  NOT NULL,
                                nombre          varchar(120) NOT NULL,
                                descripcion     varchar(250),
                                activo          boolean NOT NULL DEFAULT true,
                                creado_en       timestamp NOT NULL DEFAULT localtimestamp,
                                actualizado_en  timestamp NOT NULL DEFAULT localtimestamp,

                                CONSTRAINT uq_roles_clinicos_codigo UNIQUE (codigo)
);

CREATE TABLE especialidades (
                                id              uuid PRIMARY KEY DEFAULT uuidv7(),
                                codigo          varchar(60)  NOT NULL,
                                nombre          varchar(150) NOT NULL,
                                descripcion     varchar(250),
                                activo          boolean NOT NULL DEFAULT true,
                                creado_en       timestamp NOT NULL DEFAULT localtimestamp,
                                actualizado_en  timestamp NOT NULL DEFAULT localtimestamp,

                                CONSTRAINT uq_especialidades_codigo UNIQUE (codigo)
);

CREATE TABLE profesional_roles_clinicos (
                                            profesional_id  uuid NOT NULL REFERENCES perfiles_profesionales (id),
                                            rol_clinico_id  uuid NOT NULL REFERENCES roles_clinicos (id),
                                            creado_en       timestamp NOT NULL DEFAULT localtimestamp,

                                            PRIMARY KEY (profesional_id, rol_clinico_id)
);

CREATE TABLE profesional_especialidades (
                                            profesional_id   uuid NOT NULL REFERENCES perfiles_profesionales (id),
                                            especialidad_id  uuid NOT NULL REFERENCES especialidades (id),
                                            creado_en        timestamp NOT NULL DEFAULT localtimestamp,

                                            PRIMARY KEY (profesional_id, especialidad_id)
);

-- ---------------------------------------------------------------------
-- PROCEDIMIENTOS CONFIGURABLES
-- ---------------------------------------------------------------------

CREATE TABLE procedimientos_quirurgicos (
                                            id                         uuid PRIMARY KEY DEFAULT uuidv7(),
                                            codigo                     varchar(80)  NOT NULL,
                                            nombre                     varchar(180) NOT NULL,
                                            descripcion                text,
                                            duracion_estimada_minutos  int,
                                            activo                     boolean NOT NULL DEFAULT true,
                                            creado_en                  timestamp NOT NULL DEFAULT localtimestamp,
                                            actualizado_en             timestamp NOT NULL DEFAULT localtimestamp,

                                            CONSTRAINT uq_procedimientos_codigo UNIQUE (codigo),
                                            CONSTRAINT ck_procedimientos_duracion
                                                CHECK (duracion_estimada_minutos IS NULL OR duracion_estimada_minutos > 0)
);

CREATE TABLE procedimiento_especialidades (
                                              procedimiento_id  uuid NOT NULL REFERENCES procedimientos_quirurgicos (id),
                                              especialidad_id   uuid NOT NULL REFERENCES especialidades (id),

                                              PRIMARY KEY (procedimiento_id, especialidad_id)
);

CREATE TABLE roles_predeterminados_procedimiento (
                                                     id                       uuid PRIMARY KEY DEFAULT uuidv7(),
                                                     procedimiento_id         uuid NOT NULL REFERENCES procedimientos_quirurgicos (id),
                                                     rol_clinico_id           uuid NOT NULL REFERENCES roles_clinicos (id),
                                                     especialidad_id          uuid REFERENCES especialidades (id),
                                                     cantidad_predeterminada  int NOT NULL DEFAULT 1,
                                                     es_requerido             boolean NOT NULL DEFAULT true,
                                                     notas                    varchar(250),
                                                     creado_en                timestamp NOT NULL DEFAULT localtimestamp,
                                                     actualizado_en           timestamp NOT NULL DEFAULT localtimestamp,

                                                     CONSTRAINT ck_roles_pred_cantidad CHECK (cantidad_predeterminada > 0)
);

-- ---------------------------------------------------------------------
-- CATÁLOGO DE INSTRUMENTAL  (V4)
-- ---------------------------------------------------------------------

CREATE TABLE instrumentos_quirurgicos (
                                          id              uuid PRIMARY KEY DEFAULT uuidv7(),
                                          codigo          varchar(80)  NOT NULL,
                                          nombre          varchar(180) NOT NULL,
                                          descripcion     varchar(300),
                                          activo          boolean NOT NULL DEFAULT true,
                                          creado_en       timestamp NOT NULL DEFAULT localtimestamp,
                                          actualizado_en  timestamp NOT NULL DEFAULT localtimestamp,

                                          CONSTRAINT uq_instrumentos_codigo UNIQUE (codigo)
);

-- Bandeja o conjunto reutilizable de instrumentos
CREATE TABLE sets_instrumentales (
                                     id              uuid PRIMARY KEY DEFAULT uuidv7(),
                                     codigo          varchar(80)  NOT NULL,
                                     nombre          varchar(180) NOT NULL,
                                     descripcion     text,
                                     activo          boolean NOT NULL DEFAULT true,
                                     creado_en       timestamp NOT NULL DEFAULT localtimestamp,
                                     actualizado_en  timestamp NOT NULL DEFAULT localtimestamp,

                                     CONSTRAINT uq_sets_codigo UNIQUE (codigo)
);

CREATE TABLE instrumentos_set (
                                  set_instrumental_id  uuid NOT NULL REFERENCES sets_instrumentales (id),
                                  instrumento_id       uuid NOT NULL REFERENCES instrumentos_quirurgicos (id),
                                  cantidad             int NOT NULL DEFAULT 1,

                                  PRIMARY KEY (set_instrumental_id, instrumento_id),
                                  CONSTRAINT ck_instrumentos_set_cantidad CHECK (cantidad > 0)
);

-- Sets sugeridos automáticamente al seleccionar un procedimiento
CREATE TABLE sets_predeterminados_procedimiento (
                                                    procedimiento_id     uuid NOT NULL REFERENCES procedimientos_quirurgicos (id),
                                                    set_instrumental_id  uuid NOT NULL REFERENCES sets_instrumentales (id),
                                                    cantidad             int NOT NULL DEFAULT 1,
                                                    es_requerido         boolean NOT NULL DEFAULT true,
                                                    notas                varchar(250),

                                                    PRIMARY KEY (procedimiento_id, set_instrumental_id),
                                                    CONSTRAINT ck_sets_pred_cantidad CHECK (cantidad > 0)
);

-- Instrumentos requeridos directamente, fuera de los sets
CREATE TABLE instrumentos_predeterminados_procedimiento (
                                                            procedimiento_id  uuid NOT NULL REFERENCES procedimientos_quirurgicos (id),
                                                            instrumento_id    uuid NOT NULL REFERENCES instrumentos_quirurgicos (id),
                                                            cantidad          int NOT NULL DEFAULT 1,
                                                            es_requerido      boolean NOT NULL DEFAULT true,
                                                            notas             varchar(250),

                                                            PRIMARY KEY (procedimiento_id, instrumento_id),
                                                            CONSTRAINT ck_instr_pred_cantidad CHECK (cantidad > 0)
);

-- ---------------------------------------------------------------------
-- CONSULTAS / CITAS
-- ---------------------------------------------------------------------

CREATE TABLE citas (
                       id               uuid PRIMARY KEY DEFAULT uuidv7(),
                       paciente_id      uuid NOT NULL REFERENCES pacientes (id),
                       medico_id        uuid NOT NULL REFERENCES perfiles_profesionales (id),
                       especialidad_id  uuid NOT NULL REFERENCES especialidades (id),
                       programada_para  timestamp NOT NULL,
                       motivo           text,
                       estado           estado_cita NOT NULL DEFAULT 'SOLICITADA',
                       creado_en        timestamp NOT NULL DEFAULT localtimestamp,
                       actualizado_en   timestamp NOT NULL DEFAULT localtimestamp
);

-- ---------------------------------------------------------------------
-- SOLICITUD DE CIRUGÍA
-- ---------------------------------------------------------------------

CREATE TABLE solicitudes_cirugia (
                                     id                           uuid PRIMARY KEY DEFAULT uuidv7(),
                                     paciente_id                  uuid NOT NULL REFERENCES pacientes (id),
                                     medico_solicitante_id        uuid NOT NULL REFERENCES perfiles_profesionales (id),
                                     cita_origen_id               uuid REFERENCES citas (id),
                                     especialidad_solicitante_id  uuid NOT NULL REFERENCES especialidades (id),
                                     procedimiento_id             uuid NOT NULL REFERENCES procedimientos_quirurgicos (id),
                                     sitio_quirurgico             varchar(150),
                                     lateralidad                  lateralidad NOT NULL DEFAULT 'NO_APLICA',
                                     resumen_clinico              text,
                                     notas_medicas                text,
                                     estado                       estado_solicitud_cirugia NOT NULL DEFAULT 'BORRADOR',
                                     creado_en                    timestamp NOT NULL DEFAULT localtimestamp,
                                     enviada_en                   timestamp,
                                     actualizado_en               timestamp NOT NULL DEFAULT localtimestamp,

    -- Reglas 7 y 8: el procedimiento debe estar habilitado para la especialidad
    -- y el médico debe pertenecer a ella. Se garantizan con FKs compuestas.
                                     CONSTRAINT fk_solicitud_procedimiento_especialidad
                                         FOREIGN KEY (procedimiento_id, especialidad_solicitante_id)
                                             REFERENCES procedimiento_especialidades (procedimiento_id, especialidad_id),
                                     CONSTRAINT fk_solicitud_medico_especialidad
                                         FOREIGN KEY (medico_solicitante_id, especialidad_solicitante_id)
                                             REFERENCES profesional_especialidades (profesional_id, especialidad_id)
);

CREATE TABLE requerimientos_roles_solicitud (
                                                id                    uuid PRIMARY KEY DEFAULT uuidv7(),
                                                solicitud_cirugia_id  uuid NOT NULL REFERENCES solicitudes_cirugia (id),
                                                rol_clinico_id        uuid NOT NULL REFERENCES roles_clinicos (id),
                                                especialidad_id       uuid REFERENCES especialidades (id),
                                                cantidad              int NOT NULL DEFAULT 1,
                                                es_requerido          boolean NOT NULL DEFAULT true,
                                                notas                 varchar(250),
                                                creado_en             timestamp NOT NULL DEFAULT localtimestamp,
                                                actualizado_en        timestamp NOT NULL DEFAULT localtimestamp,

                                                CONSTRAINT ck_requerimientos_cantidad CHECK (cantidad > 0),
    -- Destino de la FK compuesta desde asignaciones
                                                CONSTRAINT uq_requerimientos_id_solicitud UNIQUE (id, solicitud_cirugia_id)
);

-- ---------------------------------------------------------------------
-- INFRAESTRUCTURA
-- ---------------------------------------------------------------------

CREATE TABLE quirofanos (
                            id              uuid PRIMARY KEY DEFAULT uuidv7(),
                            codigo          varchar(50)  NOT NULL,
                            nombre          varchar(100) NOT NULL,
                            descripcion     varchar(250),
                            activo          boolean NOT NULL DEFAULT true,
                            creado_en       timestamp NOT NULL DEFAULT localtimestamp,
                            actualizado_en  timestamp NOT NULL DEFAULT localtimestamp,

                            CONSTRAINT uq_quirofanos_codigo UNIQUE (codigo)
);

-- ---------------------------------------------------------------------
-- PROGRAMACIÓN DE CIRUGÍA
-- ---------------------------------------------------------------------

CREATE TABLE cirugias (
                          id                         uuid PRIMARY KEY DEFAULT uuidv7(),
                          solicitud_cirugia_id       uuid NOT NULL REFERENCES solicitudes_cirugia (id),
                          quirofano_id               uuid NOT NULL REFERENCES quirofanos (id),
                          programada_por_usuario_id  uuid NOT NULL REFERENCES usuarios (id),
                          coordinador_usuario_id     uuid REFERENCES usuarios (id),
                          inicio_programado          timestamp NOT NULL,
                          fin_programado             timestamp NOT NULL,
                          estado                     estado_cirugia NOT NULL DEFAULT 'PROGRAMADA',
                          notas_programacion         text,
                          creado_en                  timestamp NOT NULL DEFAULT localtimestamp,
                          actualizado_en             timestamp NOT NULL DEFAULT localtimestamp,

    -- Una solicitud = una cirugía. Cancelada/suspendida no se reprograma.
                          CONSTRAINT uq_cirugias_solicitud UNIQUE (solicitud_cirugia_id),
    -- Destino de la FK compuesta desde asignaciones
                          CONSTRAINT uq_cirugias_id_solicitud UNIQUE (id, solicitud_cirugia_id),

    -- Regla 6
                          CONSTRAINT ck_cirugias_horario CHECK (fin_programado > inicio_programado),

    -- Reglas 5 y 18: un quirófano no puede tener dos cirugías vigentes solapadas.
    -- '[)' permite que una termine 10:00 y la siguiente empiece 10:00.
                          CONSTRAINT ex_cirugias_quirofano_sin_solape
                              EXCLUDE USING gist (
      quirofano_id WITH =,
      tsrange(inicio_programado, fin_programado, '[)') WITH &&
    ) WHERE (estado NOT IN ('CANCELADA', 'SUSPENDIDA'))
);

CREATE TABLE asignaciones_personal_cirugia (
                                               id                       uuid PRIMARY KEY DEFAULT uuidv7(),
                                               cirugia_id               uuid NOT NULL,
                                               solicitud_cirugia_id     uuid NOT NULL,   -- solo para amarrar cirugía y requerimiento a la misma solicitud
                                               requerimiento_rol_id     uuid NOT NULL,
                                               profesional_id           uuid NOT NULL REFERENCES perfiles_profesionales (id),
                                               estado                   estado_asignacion NOT NULL DEFAULT 'ASIGNADA',
    -- V3: persona que opera físicamente el tablero en ESTA cirugía.
    -- No depende del rol clínico (un AUXILIAR_CIRCULANTE no lo es automáticamente).
                                               es_operador_tablero      boolean NOT NULL DEFAULT false,
                                               asignado_por_usuario_id  uuid NOT NULL REFERENCES usuarios (id),
                                               asignado_en              timestamp NOT NULL DEFAULT localtimestamp,
                                               confirmado_en            timestamp,
                                               notas                    varchar(250),

    -- La cirugía y el requerimiento deben pertenecer a la MISMA solicitud
                                               CONSTRAINT fk_asignacion_cirugia
                                                   FOREIGN KEY (cirugia_id, solicitud_cirugia_id)
                                                       REFERENCES cirugias (id, solicitud_cirugia_id),
                                               CONSTRAINT fk_asignacion_requerimiento
                                                   FOREIGN KEY (requerimiento_rol_id, solicitud_cirugia_id)
                                                       REFERENCES requerimientos_roles_solicitud (id, solicitud_cirugia_id),

    -- Una persona = un rol por cirugía. Rechazada/cancelada se reactiva, no se re-inserta.
                                               CONSTRAINT uq_asignacion_cirugia_profesional UNIQUE (cirugia_id, profesional_id),
    -- Destino de FKs compuestas desde hitos, recuentos, eventos
                                               CONSTRAINT uq_asignacion_id_cirugia UNIQUE (id, cirugia_id),

                                               CONSTRAINT ck_asignacion_confirmada
                                                   CHECK (estado <> 'CONFIRMADA' OR confirmado_en IS NOT NULL)
);

CREATE TABLE disponibilidad_profesional (
                                            id                   uuid PRIMARY KEY DEFAULT uuidv7(),
                                            profesional_id       uuid NOT NULL REFERENCES perfiles_profesionales (id),
                                            tipo_disponibilidad  tipo_disponibilidad NOT NULL,
                                            inicia_en            timestamp NOT NULL,
                                            termina_en           timestamp NOT NULL,
                                            notas                varchar(250),
                                            creado_en            timestamp NOT NULL DEFAULT localtimestamp,
                                            actualizado_en       timestamp NOT NULL DEFAULT localtimestamp,

                                            CONSTRAINT ck_disponibilidad_rango CHECK (termina_en > inicia_en)
);

-- ---------------------------------------------------------------------
-- INSTRUMENTAL DE LA CIRUGÍA — SNAPSHOT  (V4)
-- Se llena con fn_preparar_instrumental(cirugia_id). Conserva el histórico
-- aunque luego cambie el catálogo.
-- es_requerido se copia del predeterminado: lo necesita la regla
-- "no iniciar cirugía si falta instrumental obligatorio".
-- ---------------------------------------------------------------------

CREATE TABLE sets_instrumentales_cirugia (
                                             id                           uuid PRIMARY KEY DEFAULT uuidv7(),
                                             cirugia_id                   uuid NOT NULL REFERENCES cirugias (id),
                                             set_origen_id                uuid REFERENCES sets_instrumentales (id),
                                             codigo_set                   varchar(80)  NOT NULL,
                                             nombre_set                   varchar(180) NOT NULL,
                                             es_requerido                 boolean NOT NULL DEFAULT true,
                                             cantidad_requerida           int NOT NULL DEFAULT 1,
                                             cantidad_preparada           int NOT NULL DEFAULT 0,
                                             preparado_por_asignacion_id  uuid,
                                             preparado_en                 timestamp,
                                             notas                        text,
                                             creado_en                    timestamp NOT NULL DEFAULT localtimestamp,
                                             actualizado_en               timestamp NOT NULL DEFAULT localtimestamp,

                                             CONSTRAINT uq_sets_cirugia_id_cirugia UNIQUE (id, cirugia_id),
                                             CONSTRAINT fk_sets_cirugia_preparado_por
                                                 FOREIGN KEY (preparado_por_asignacion_id, cirugia_id)
                                                     REFERENCES asignaciones_personal_cirugia (id, cirugia_id),
                                             CONSTRAINT ck_sets_cirugia_cantidades
                                                 CHECK (cantidad_requerida > 0 AND cantidad_preparada >= 0),
                                             CONSTRAINT ck_sets_cirugia_preparado
                                                 CHECK ((preparado_por_asignacion_id IS NULL) = (preparado_en IS NULL))
);

CREATE TABLE instrumentos_cirugia (
                                      id                           uuid PRIMARY KEY DEFAULT uuidv7(),
                                      cirugia_id                   uuid NOT NULL REFERENCES cirugias (id),
                                      set_cirugia_id               uuid,   -- NULL: instrumento requerido directamente
                                      instrumento_origen_id        uuid REFERENCES instrumentos_quirurgicos (id),
                                      codigo_instrumento           varchar(80)  NOT NULL,
                                      nombre_instrumento           varchar(180) NOT NULL,
                                      es_requerido                 boolean NOT NULL DEFAULT true,
                                      cantidad_requerida           int NOT NULL DEFAULT 1,
                                      cantidad_preparada           int NOT NULL DEFAULT 0,
                                      preparado_por_asignacion_id  uuid,
                                      preparado_en                 timestamp,
                                      notas                        text,
                                      creado_en                    timestamp NOT NULL DEFAULT localtimestamp,
                                      actualizado_en               timestamp NOT NULL DEFAULT localtimestamp,

                                      CONSTRAINT uq_instrumentos_cirugia_id_cirugia UNIQUE (id, cirugia_id),
    -- El set debe ser de la MISMA cirugía
                                      CONSTRAINT fk_instrumentos_cirugia_set
                                          FOREIGN KEY (set_cirugia_id, cirugia_id)
                                              REFERENCES sets_instrumentales_cirugia (id, cirugia_id),
                                      CONSTRAINT fk_instrumentos_cirugia_preparado_por
                                          FOREIGN KEY (preparado_por_asignacion_id, cirugia_id)
                                              REFERENCES asignaciones_personal_cirugia (id, cirugia_id),
                                      CONSTRAINT ck_instrumentos_cirugia_cantidades
                                          CHECK (cantidad_requerida > 0 AND cantidad_preparada >= 0),
                                      CONSTRAINT ck_instrumentos_cirugia_preparado
                                          CHECK ((preparado_por_asignacion_id IS NULL) = (preparado_en IS NULL))
);

-- ---------------------------------------------------------------------
-- DATOS PREOPERATORIOS / SNAPSHOT
-- ---------------------------------------------------------------------

CREATE TABLE datos_preoperatorios_cirugia (
                                              id                             uuid PRIMARY KEY DEFAULT uuidv7(),
                                              cirugia_id                     uuid NOT NULL REFERENCES cirugias (id),
                                              peso_kg                        numeric(6,2),
                                              talla_cm                       numeric(6,2),
                                              glucometria_mg_dl              numeric(7,2),
                                              requiere_reserva_sangre        boolean NOT NULL DEFAULT false,
                                              estado_reserva_sangre          estado_reserva_sangre NOT NULL DEFAULT 'NO_REQUERIDA',
                                              informacion_clinica_relevante  text,
                                              copia_alergias                 jsonb,   -- snapshot de alergias_paciente al momento de la cirugía
                                              datos_adicionales              jsonb,
                                              validado_por_usuario_id        uuid REFERENCES usuarios (id),
                                              validado_en                    timestamp,
                                              creado_en                      timestamp NOT NULL DEFAULT localtimestamp,
                                              actualizado_en                 timestamp NOT NULL DEFAULT localtimestamp,

                                              CONSTRAINT uq_preop_cirugia UNIQUE (cirugia_id),
                                              CONSTRAINT ck_preop_peso       CHECK (peso_kg IS NULL OR peso_kg > 0),
                                              CONSTRAINT ck_preop_talla      CHECK (talla_cm IS NULL OR talla_cm > 0),
                                              CONSTRAINT ck_preop_glucometria CHECK (glucometria_mg_dl IS NULL OR glucometria_mg_dl > 0),
                                              CONSTRAINT ck_preop_reserva_sangre
                                                  CHECK (requiere_reserva_sangre OR estado_reserva_sangre = 'NO_REQUERIDA'),
                                              CONSTRAINT ck_preop_validacion
                                                  CHECK ((validado_por_usuario_id IS NULL) = (validado_en IS NULL))
);

-- ---------------------------------------------------------------------
-- PLANTILLAS DE CHECKLIST
-- ---------------------------------------------------------------------

CREATE TABLE plantillas_checklist (
                                      id              uuid PRIMARY KEY DEFAULT uuidv7(),
                                      codigo          varchar(80)  NOT NULL,
                                      nombre          varchar(180) NOT NULL,
                                      descripcion     text,
                                      version         int NOT NULL,
                                      estado          estado_plantilla NOT NULL DEFAULT 'BORRADOR',
                                      publicada_en    timestamp,
                                      creado_en       timestamp NOT NULL DEFAULT localtimestamp,
                                      actualizado_en  timestamp NOT NULL DEFAULT localtimestamp,

                                      CONSTRAINT uq_plantillas_codigo_version UNIQUE (codigo, version),
                                      CONSTRAINT ck_plantillas_version CHECK (version > 0),
                                      CONSTRAINT ck_plantillas_publicada
                                          CHECK (estado = 'BORRADOR' OR publicada_en IS NOT NULL)
);

CREATE TABLE fases_plantilla_checklist (
                                           id            uuid PRIMARY KEY DEFAULT uuidv7(),
                                           plantilla_id  uuid NOT NULL REFERENCES plantillas_checklist (id),
                                           codigo        varchar(80)  NOT NULL,
                                           nombre        varchar(150) NOT NULL,
                                           descripcion   text,
                                           orden         int NOT NULL,

                                           CONSTRAINT uq_fases_plantilla_codigo UNIQUE (plantilla_id, codigo)
);

CREATE TABLE items_plantilla_checklist (
                                           id                          uuid PRIMARY KEY DEFAULT uuidv7(),
                                           fase_id                     uuid NOT NULL REFERENCES fases_plantilla_checklist (id),
                                           codigo                      varchar(100) NOT NULL,
                                           etiqueta                    varchar(250) NOT NULL,
                                           descripcion                 text,
                                           tipo_respuesta              varchar(50) NOT NULL,
                                           obligatorio                 boolean NOT NULL DEFAULT true,
                                           bloqueante                  boolean NOT NULL DEFAULT false,
                                           rol_clinico_responsable_id  uuid REFERENCES roles_clinicos (id),
                                           config_validacion           jsonb,
                                           orden                       int NOT NULL,
                                           creado_en                   timestamp NOT NULL DEFAULT localtimestamp,
                                           actualizado_en              timestamp NOT NULL DEFAULT localtimestamp
);

CREATE TABLE procedimiento_plantillas_checklist (
                                                    procedimiento_id        uuid NOT NULL REFERENCES procedimientos_quirurgicos (id),
                                                    plantilla_checklist_id  uuid NOT NULL REFERENCES plantillas_checklist (id),
                                                    es_predeterminada       boolean NOT NULL DEFAULT false,

                                                    PRIMARY KEY (procedimiento_id, plantilla_checklist_id)
);

-- ---------------------------------------------------------------------
-- CHECKLIST EJECUTADO
-- ---------------------------------------------------------------------

CREATE TABLE checklists_cirugia (
                                    id                   uuid PRIMARY KEY DEFAULT uuidv7(),
                                    cirugia_id           uuid NOT NULL REFERENCES cirugias (id),
                                    plantilla_origen_id  uuid NOT NULL REFERENCES plantillas_checklist (id),
                                    codigo_plantilla     varchar(80)  NOT NULL,
                                    nombre_plantilla     varchar(180) NOT NULL,
                                    version_plantilla    int NOT NULL,
                                    estado               estado_checklist NOT NULL DEFAULT 'PENDIENTE',
                                    iniciado_en          timestamp,
                                    completado_en        timestamp,
                                    creado_en            timestamp NOT NULL DEFAULT localtimestamp,
                                    actualizado_en       timestamp NOT NULL DEFAULT localtimestamp,

                                    CONSTRAINT uq_checklists_id_cirugia UNIQUE (id, cirugia_id),
                                    CONSTRAINT ck_checklist_completado
                                        CHECK (estado <> 'COMPLETADO' OR completado_en IS NOT NULL)
);

CREATE TABLE fases_checklist_cirugia (
                                         id                         uuid PRIMARY KEY DEFAULT uuidv7(),
                                         cirugia_id                 uuid NOT NULL,
                                         checklist_cirugia_id       uuid NOT NULL,
                                         fase_plantilla_origen_id   uuid REFERENCES fases_plantilla_checklist (id),
                                         codigo_fase                varchar(80)  NOT NULL,
                                         nombre_fase                varchar(150) NOT NULL,
                                         orden                      int NOT NULL,
                                         estado                     estado_fase_checklist NOT NULL DEFAULT 'PENDIENTE',
                                         iniciada_en                timestamp,
                                         cerrada_por_asignacion_id  uuid,
                                         cerrada_en                 timestamp,
                                         notas                      text,
                                         creado_en                  timestamp NOT NULL DEFAULT localtimestamp,
                                         actualizado_en             timestamp NOT NULL DEFAULT localtimestamp,

                                         CONSTRAINT uq_fases_cirugia_codigo UNIQUE (checklist_cirugia_id, codigo_fase),
                                         CONSTRAINT uq_fases_cirugia_orden  UNIQUE (checklist_cirugia_id, orden),
                                         CONSTRAINT uq_fases_cirugia_id_cirugia UNIQUE (id, cirugia_id),
                                         CONSTRAINT fk_fase_checklist
                                             FOREIGN KEY (checklist_cirugia_id, cirugia_id)
                                                 REFERENCES checklists_cirugia (id, cirugia_id),
                                         CONSTRAINT fk_fase_cerrada_por
                                             FOREIGN KEY (cerrada_por_asignacion_id, cirugia_id)
                                                 REFERENCES asignaciones_personal_cirugia (id, cirugia_id),
    -- Una fase completada debe decir quién la cerró y cuándo
                                         CONSTRAINT ck_fase_cierre
                                             CHECK (estado <> 'COMPLETADA'
                                                 OR (cerrada_por_asignacion_id IS NOT NULL AND cerrada_en IS NOT NULL))
);

CREATE TABLE items_checklist_cirugia (
                                         id                            uuid PRIMARY KEY DEFAULT uuidv7(),
                                         cirugia_id                    uuid NOT NULL,
                                         fase_cirugia_id               uuid NOT NULL,
                                         item_plantilla_origen_id      uuid REFERENCES items_plantilla_checklist (id),
                                         codigo_item                   varchar(100) NOT NULL,
                                         etiqueta                      varchar(250) NOT NULL,
                                         descripcion                   text,
                                         tipo_respuesta                varchar(50) NOT NULL,
                                         obligatorio                   boolean NOT NULL,
                                         bloqueante                    boolean NOT NULL,
                                         rol_clinico_responsable_id    uuid REFERENCES roles_clinicos (id),
                                         estado                        estado_item_checklist NOT NULL DEFAULT 'PENDIENTE',
                                         respuesta                     jsonb,
    -- V3: quién introdujo físicamente la respuesta en el tablero
                                         registrado_por_asignacion_id  uuid,
                                         registrado_en                 timestamp,
                                         notas                         text,
                                         orden                         int NOT NULL,
                                         creado_en                     timestamp NOT NULL DEFAULT localtimestamp,
                                         actualizado_en                timestamp NOT NULL DEFAULT localtimestamp,

                                         CONSTRAINT uq_items_cirugia_codigo UNIQUE (fase_cirugia_id, codigo_item),
                                         CONSTRAINT uq_items_cirugia_id_cirugia UNIQUE (id, cirugia_id),
                                         CONSTRAINT fk_item_fase
                                             FOREIGN KEY (fase_cirugia_id, cirugia_id)
                                                 REFERENCES fases_checklist_cirugia (id, cirugia_id),
                                         CONSTRAINT fk_item_registrado_por
                                             FOREIGN KEY (registrado_por_asignacion_id, cirugia_id)
                                                 REFERENCES asignaciones_personal_cirugia (id, cirugia_id),
                                         CONSTRAINT ck_item_registro
                                             CHECK ((registrado_por_asignacion_id IS NULL) = (registrado_en IS NULL))
);

-- V3: confirmación CLÍNICA de un ítem, independiente de quién opera el tablero.
-- Ej: confirmado por la Dra. Laura (anestesióloga), registrado por Carlos (circulante).
CREATE TABLE confirmaciones_item_checklist (
                                               id                         uuid PRIMARY KEY DEFAULT uuidv7(),
                                               cirugia_id                 uuid NOT NULL,
                                               item_checklist_cirugia_id  uuid NOT NULL,
                                               asignacion_personal_id     uuid NOT NULL,
                                               resultado                  resultado_confirmacion NOT NULL,
                                               confirmado_en              timestamp NOT NULL,
                                               notas                      varchar(250),
                                               creado_en                  timestamp NOT NULL DEFAULT localtimestamp,

                                               CONSTRAINT uq_confirmacion_item_persona UNIQUE (item_checklist_cirugia_id, asignacion_personal_id),
                                               CONSTRAINT fk_confirmacion_item
                                                   FOREIGN KEY (item_checklist_cirugia_id, cirugia_id)
                                                       REFERENCES items_checklist_cirugia (id, cirugia_id),
                                               CONSTRAINT fk_confirmacion_item_asignacion
                                                   FOREIGN KEY (asignacion_personal_id, cirugia_id)
                                                       REFERENCES asignaciones_personal_cirugia (id, cirugia_id)
);

CREATE TABLE recuentos_cirugia (
                                   id                                 uuid PRIMARY KEY DEFAULT uuidv7(),
                                   cirugia_id                         uuid NOT NULL REFERENCES cirugias (id),
                                   tipo_recuento                      tipo_recuento NOT NULL,
                                   instrumento_cirugia_id             uuid,   -- V4: opcional, liga el recuento a un instrumento del snapshot
                                   descripcion                        varchar(150),
                                   cantidad_inicial                   int NOT NULL,
                                   registrado_inicial_por_asignacion_id  uuid NOT NULL,   -- quién lo digitó en el tablero
                                   registrado_inicial_en                 timestamp NOT NULL,
                                   cantidad_agregada                  int NOT NULL DEFAULT 0,   -- material abierto durante la cirugía
                                   cantidad_final                     int,
                                   registrado_final_por_asignacion_id    uuid,
                                   registrado_final_en                   timestamp,
                                   notas                              text,
                                   creado_en                          timestamp NOT NULL DEFAULT localtimestamp,
                                   actualizado_en                     timestamp NOT NULL DEFAULT localtimestamp,

    -- Quien registra debe estar asignado a ESTA cirugía
                                   CONSTRAINT fk_recuento_inicial_asignacion
                                       FOREIGN KEY (registrado_inicial_por_asignacion_id, cirugia_id)
                                           REFERENCES asignaciones_personal_cirugia (id, cirugia_id),
                                   CONSTRAINT fk_recuento_final_asignacion
                                       FOREIGN KEY (registrado_final_por_asignacion_id, cirugia_id)
                                           REFERENCES asignaciones_personal_cirugia (id, cirugia_id),

                                   CONSTRAINT uq_recuentos_id_cirugia UNIQUE (id, cirugia_id),
                                   CONSTRAINT fk_recuento_instrumento
                                       FOREIGN KEY (instrumento_cirugia_id, cirugia_id)
                                           REFERENCES instrumentos_cirugia (id, cirugia_id),

                                   CONSTRAINT ck_recuento_cantidades
                                       CHECK (cantidad_inicial >= 0 AND cantidad_agregada >= 0
                                           AND (cantidad_final IS NULL OR cantidad_final >= 0)),
    -- El conteo final se registra completo (cantidad + quién + cuándo) o no se registra
                                   CONSTRAINT ck_recuento_final_completo
                                       CHECK ((cantidad_final IS NULL AND registrado_final_por_asignacion_id IS NULL AND registrado_final_en IS NULL)
                                           OR (cantidad_final IS NOT NULL AND registrado_final_por_asignacion_id IS NOT NULL AND registrado_final_en IS NOT NULL))
);

-- V3: confirmación CLÍNICA de un conteo (inicial o final), separada de quién lo digita.
CREATE TABLE confirmaciones_recuento (
                                         id                      uuid PRIMARY KEY DEFAULT uuidv7(),
                                         cirugia_id              uuid NOT NULL,
                                         recuento_cirugia_id     uuid NOT NULL,
                                         etapa                   etapa_recuento NOT NULL,
                                         asignacion_personal_id  uuid NOT NULL,
                                         confirmado_en           timestamp NOT NULL,
                                         notas                   varchar(250),
                                         creado_en               timestamp NOT NULL DEFAULT localtimestamp,

                                         CONSTRAINT uq_confirmacion_recuento UNIQUE (recuento_cirugia_id, etapa, asignacion_personal_id),
                                         CONSTRAINT fk_confirmacion_recuento
                                             FOREIGN KEY (recuento_cirugia_id, cirugia_id)
                                                 REFERENCES recuentos_cirugia (id, cirugia_id),
                                         CONSTRAINT fk_confirmacion_recuento_asignacion
                                             FOREIGN KEY (asignacion_personal_id, cirugia_id)
                                                 REFERENCES asignaciones_personal_cirugia (id, cirugia_id)
);

-- El estado del recuento NO se guarda: se calcula aquí.
CREATE VIEW v_recuentos_cirugia AS
SELECT
    r.*,
    r.cantidad_inicial + r.cantidad_agregada AS cantidad_esperada,
    CASE
        WHEN r.cantidad_final IS NULL                                   THEN 'PENDIENTE'
        WHEN r.cantidad_final = r.cantidad_inicial + r.cantidad_agregada THEN 'CUADRA'
        ELSE 'DISCREPANCIA'
        END AS estado_recuento
FROM recuentos_cirugia r;

-- ---------------------------------------------------------------------
-- MOTOR DE REGLAS Y ALERTAS
-- ---------------------------------------------------------------------

-- Validaciones CLÍNICAS (no autorización). La lógica de cada regla vive en
-- el backend (una función por regla, identificada por codigo). Esta tabla
-- solo configura: activa/inactiva, severidad y si bloquea.
CREATE TABLE reglas_seguridad (
                                  id              uuid PRIMARY KEY DEFAULT uuidv7(),
                                  codigo          varchar(100) NOT NULL,
                                  nombre          varchar(180) NOT NULL,
                                  descripcion     text,
                                  severidad       severidad_alerta NOT NULL,
                                  bloqueante      boolean NOT NULL DEFAULT false,
                                  condicion       jsonb,   -- reservado para reglas configurables en el futuro
                                  activo          boolean NOT NULL DEFAULT true,
                                  creado_en       timestamp NOT NULL DEFAULT localtimestamp,
                                  actualizado_en  timestamp NOT NULL DEFAULT localtimestamp,

                                  CONSTRAINT uq_reglas_codigo UNIQUE (codigo)
);

CREATE TABLE alertas (
                         id                                   uuid PRIMARY KEY DEFAULT uuidv7(),
                         cirugia_id                           uuid NOT NULL REFERENCES cirugias (id),
                         regla_seguridad_id                   uuid REFERENCES reglas_seguridad (id),
                         tipo_origen                          varchar(60),
                         origen_id                            uuid,
                         severidad                            severidad_alerta NOT NULL,
                         bloqueante                           boolean NOT NULL DEFAULT false,
                         estado                               estado_alerta NOT NULL DEFAULT 'ABIERTA',
                         titulo                               varchar(180) NOT NULL,
                         mensaje                              text NOT NULL,
                         disparada_en                         timestamp NOT NULL DEFAULT localtimestamp,
                         reconocida_por_usuario_id            uuid REFERENCES usuarios (id),
                         reconocida_en                        timestamp,
                         resuelta_por_usuario_id              uuid REFERENCES usuarios (id),
                         resuelta_en                          timestamp,
                         notas_resolucion                     text,
                         excepcion_autorizada_por_usuario_id  uuid REFERENCES usuarios (id),
                         excepcion_autorizada_en              timestamp,
                         motivo_excepcion                     text,

                         CONSTRAINT ck_alerta_reconocimiento
                             CHECK ((reconocida_por_usuario_id IS NULL) = (reconocida_en IS NULL)),
    -- Resolver o descartar exige quién, cuándo y por qué
                         CONSTRAINT ck_alerta_cierre
                             CHECK (estado NOT IN ('RESUELTA', 'DESCARTADA')
                                 OR (resuelta_por_usuario_id IS NOT NULL
                                     AND resuelta_en IS NOT NULL
                                     AND notas_resolucion IS NOT NULL AND btrim(notas_resolucion) <> '')),
    -- Regla 16: la excepción va completa (usuario + fecha + motivo) o no va
                         CONSTRAINT ck_alerta_excepcion
                             CHECK ((excepcion_autorizada_por_usuario_id IS NULL AND excepcion_autorizada_en IS NULL AND motivo_excepcion IS NULL)
                                 OR (excepcion_autorizada_por_usuario_id IS NOT NULL AND excepcion_autorizada_en IS NOT NULL
                                     AND motivo_excepcion IS NOT NULL AND btrim(motivo_excepcion) <> '')),
    -- Solo tiene sentido exceptuar una alerta bloqueante
                         CONSTRAINT ck_alerta_excepcion_bloqueante
                             CHECK (excepcion_autorizada_por_usuario_id IS NULL OR bloqueante)
);

-- ---------------------------------------------------------------------
-- HITOS Y TIMELINE
-- ---------------------------------------------------------------------

CREATE TABLE hitos_cirugia (
                               id                            uuid PRIMARY KEY DEFAULT uuidv7(),
                               cirugia_id                    uuid NOT NULL REFERENCES cirugias (id),
                               tipo_hito                     tipo_hito NOT NULL,
                               ocurrido_en                   timestamp NOT NULL,
                               registrado_por_asignacion_id  uuid,
                               notas                         varchar(250),
                               corrige_hito_id               uuid REFERENCES hitos_cirugia (id),
                               anulado_en                    timestamp,
                               anulado_por_usuario_id        uuid REFERENCES usuarios (id),
                               motivo_anulacion              varchar(250),
                               creado_en                     timestamp NOT NULL DEFAULT localtimestamp,

                               CONSTRAINT fk_hito_asignacion
                                   FOREIGN KEY (registrado_por_asignacion_id, cirugia_id)
                                       REFERENCES asignaciones_personal_cirugia (id, cirugia_id),
                               CONSTRAINT ck_hito_no_se_corrige_a_si_mismo
                                   CHECK (corrige_hito_id IS NULL OR corrige_hito_id <> id),
    -- La anulación va completa o no va
                               CONSTRAINT ck_hito_anulacion
                                   CHECK ((anulado_en IS NULL AND anulado_por_usuario_id IS NULL AND motivo_anulacion IS NULL)
                                       OR (anulado_en IS NOT NULL AND anulado_por_usuario_id IS NOT NULL
                                           AND motivo_anulacion IS NOT NULL AND btrim(motivo_anulacion) <> ''))
);

CREATE TABLE eventos_cirugia (
                                 id                        uuid PRIMARY KEY DEFAULT uuidv7(),
                                 cirugia_id                uuid NOT NULL REFERENCES cirugias (id),
                                 tipo_evento               varchar(100) NOT NULL,
                                 actor_usuario_id          uuid REFERENCES usuarios (id),
                                 actor_asignacion_id       uuid,
                                 tipo_entidad_relacionada  varchar(100),
                                 entidad_relacionada_id    uuid,
                                 datos                     jsonb,
                                 ocurrido_en               timestamp NOT NULL DEFAULT localtimestamp,

                                 CONSTRAINT fk_evento_asignacion
                                     FOREIGN KEY (actor_asignacion_id, cirugia_id)
                                         REFERENCES asignaciones_personal_cirugia (id, cirugia_id)
);

-- ---------------------------------------------------------------------
-- INCIDENTES / NOVEDADES
-- ---------------------------------------------------------------------

CREATE TABLE incidentes (
                            id                        uuid PRIMARY KEY DEFAULT uuidv7(),
                            cirugia_id                uuid NOT NULL REFERENCES cirugias (id),
                            categoria                 varchar(100),
                            severidad                 severidad_alerta,
                            descripcion               text NOT NULL,
                            estado                    estado_incidente NOT NULL DEFAULT 'ABIERTO',
                            reportado_por_usuario_id  uuid NOT NULL REFERENCES usuarios (id),
                            reportado_en              timestamp NOT NULL DEFAULT localtimestamp,
                            resuelto_por_usuario_id   uuid REFERENCES usuarios (id),
                            resuelto_en               timestamp,
                            notas_resolucion          text,
                            creado_en                 timestamp NOT NULL DEFAULT localtimestamp,
                            actualizado_en            timestamp NOT NULL DEFAULT localtimestamp,

                            CONSTRAINT ck_incidente_resuelto
                                CHECK (estado <> 'RESUELTO'
                                    OR (resuelto_por_usuario_id IS NOT NULL AND resuelto_en IS NOT NULL))
);

-- ---------------------------------------------------------------------
-- AUDITORÍA GENERAL
-- ---------------------------------------------------------------------

CREATE TABLE registros_auditoria (
                                     id                  uuid PRIMARY KEY DEFAULT uuidv7(),
                                     usuario_id          uuid REFERENCES usuarios (id),
                                     tipo_entidad        varchar(100) NOT NULL,
                                     entidad_id          uuid,
                                     accion              varchar(100) NOT NULL,
                                     valores_anteriores  jsonb,
                                     valores_nuevos      jsonb,
                                     metadatos           jsonb,
                                     ocurrido_en         timestamp NOT NULL DEFAULT localtimestamp
);


-- =====================================================================
-- 3. ÍNDICES
--    (PostgreSQL no indexa automáticamente las FKs; estos cubren las
--     columnas de búsqueda y unión más frecuentes.)
-- =====================================================================

CREATE INDEX ix_usuarios_estado                     ON usuarios (estado);
CREATE INDEX ix_quirofanos_activo                   ON quirofanos (activo);
CREATE INDEX ix_usuario_roles_rol                   ON usuario_roles_sistema (rol_sistema_id);
CREATE INDEX ix_rol_permisos_permiso                ON rol_sistema_permisos (permiso_id);

CREATE INDEX ix_pacientes_numero_documento          ON pacientes (numero_documento);
CREATE INDEX ix_alergias_paciente_activas           ON alergias_paciente (paciente_id, activo);

CREATE INDEX ix_perfiles_numero_profesional         ON perfiles_profesionales (numero_profesional);
CREATE INDEX ix_prof_roles_rol                      ON profesional_roles_clinicos (rol_clinico_id);
CREATE INDEX ix_prof_especialidades_especialidad    ON profesional_especialidades (especialidad_id);

CREATE INDEX ix_proc_especialidades_especialidad    ON procedimiento_especialidades (especialidad_id);
CREATE INDEX ix_roles_pred_procedimiento            ON roles_predeterminados_procedimiento (procedimiento_id);
CREATE INDEX ix_roles_pred_rol                      ON roles_predeterminados_procedimiento (rol_clinico_id);

CREATE INDEX ix_citas_paciente                      ON citas (paciente_id);
CREATE INDEX ix_citas_medico_fecha                  ON citas (medico_id, programada_para);
CREATE INDEX ix_citas_especialidad                  ON citas (especialidad_id);

CREATE INDEX ix_solicitudes_paciente                ON solicitudes_cirugia (paciente_id);
CREATE INDEX ix_solicitudes_medico                  ON solicitudes_cirugia (medico_solicitante_id);
CREATE INDEX ix_solicitudes_procedimiento           ON solicitudes_cirugia (procedimiento_id);
CREATE INDEX ix_solicitudes_estado_fecha            ON solicitudes_cirugia (estado, creado_en);

CREATE INDEX ix_requerimientos_solicitud            ON requerimientos_roles_solicitud (solicitud_cirugia_id);
CREATE INDEX ix_requerimientos_rol                  ON requerimientos_roles_solicitud (rol_clinico_id);

CREATE INDEX ix_cirugias_quirofano                  ON cirugias (quirofano_id);
CREATE INDEX ix_cirugias_inicio                     ON cirugias (inicio_programado);
CREATE INDEX ix_cirugias_estado                     ON cirugias (estado);

CREATE INDEX ix_asignaciones_requerimiento          ON asignaciones_personal_cirugia (requerimiento_rol_id);
CREATE INDEX ix_asignaciones_profesional            ON asignaciones_personal_cirugia (profesional_id);

-- V3: como máximo UN operador del tablero vigente por cirugía
CREATE UNIQUE INDEX ux_cirugia_operador_tablero
    ON asignaciones_personal_cirugia (cirugia_id)
    WHERE es_operador_tablero AND estado IN ('ASIGNADA', 'CONFIRMADA');

CREATE INDEX ix_disponibilidad_profesional_rango
    ON disponibilidad_profesional USING gist (profesional_id, tsrange(inicia_en, termina_en, '[)'));

-- Solo una plantilla predeterminada por procedimiento
CREATE UNIQUE INDEX uq_proc_plantilla_predeterminada
    ON procedimiento_plantillas_checklist (procedimiento_id) WHERE es_predeterminada;
CREATE INDEX ix_proc_plantillas_plantilla           ON procedimiento_plantillas_checklist (plantilla_checklist_id);

CREATE INDEX ix_plantillas_estado                   ON plantillas_checklist (estado);
CREATE INDEX ix_items_plantilla_fase                ON items_plantilla_checklist (fase_id);
CREATE INDEX ix_items_plantilla_rol                 ON items_plantilla_checklist (rol_clinico_responsable_id);

CREATE INDEX ix_checklists_cirugia                  ON checklists_cirugia (cirugia_id);
CREATE INDEX ix_checklists_plantilla                ON checklists_cirugia (plantilla_origen_id);
CREATE INDEX ix_fases_cirugia_cerrada_por           ON fases_checklist_cirugia (cerrada_por_asignacion_id);
CREATE INDEX ix_items_cirugia_estado                ON items_checklist_cirugia (estado);
CREATE INDEX ix_items_cirugia_registrado_por        ON items_checklist_cirugia (registrado_por_asignacion_id);
CREATE INDEX ix_confirm_item_asignacion             ON confirmaciones_item_checklist (asignacion_personal_id);
CREATE INDEX ix_confirm_item_cirugia                ON confirmaciones_item_checklist (cirugia_id);
CREATE INDEX ix_fases_cirugia_cirugia               ON fases_checklist_cirugia (cirugia_id);
CREATE INDEX ix_items_cirugia_cirugia               ON items_checklist_cirugia (cirugia_id);
CREATE INDEX ix_confirm_item_fecha                  ON confirmaciones_item_checklist (confirmado_en);

CREATE INDEX ix_recuentos_cirugia                   ON recuentos_cirugia (cirugia_id);
CREATE INDEX ix_confirm_recuento_etapa              ON confirmaciones_recuento (recuento_cirugia_id, etapa);
CREATE INDEX ix_confirm_recuento_asignacion         ON confirmaciones_recuento (asignacion_personal_id);
CREATE INDEX ix_confirm_recuento_cirugia            ON confirmaciones_recuento (cirugia_id);
CREATE INDEX ix_recuentos_instrumento               ON recuentos_cirugia (instrumento_cirugia_id);

CREATE INDEX ix_instrumentos_activo                 ON instrumentos_quirurgicos (activo);
CREATE INDEX ix_sets_activo                         ON sets_instrumentales (activo);
CREATE INDEX ix_instrumentos_set_instrumento        ON instrumentos_set (instrumento_id);
CREATE INDEX ix_sets_pred_set                       ON sets_predeterminados_procedimiento (set_instrumental_id);
CREATE INDEX ix_instr_pred_instrumento              ON instrumentos_predeterminados_procedimiento (instrumento_id);
CREATE INDEX ix_sets_cirugia_cirugia                ON sets_instrumentales_cirugia (cirugia_id);
CREATE INDEX ix_sets_cirugia_origen                 ON sets_instrumentales_cirugia (set_origen_id);
CREATE INDEX ix_instrumentos_cirugia_cirugia        ON instrumentos_cirugia (cirugia_id);
CREATE INDEX ix_instrumentos_cirugia_set            ON instrumentos_cirugia (set_cirugia_id);
CREATE INDEX ix_instrumentos_cirugia_origen         ON instrumentos_cirugia (instrumento_origen_id);
-- Un recuento por tipo+descripción en cada cirugía (NULLS NOT DISTINCT: dos GASAS sin descripción chocan)
CREATE UNIQUE INDEX uq_recuentos_tipo
    ON recuentos_cirugia (cirugia_id, tipo_recuento, descripcion) NULLS NOT DISTINCT;

CREATE INDEX ix_reglas_activo                       ON reglas_seguridad (activo);

CREATE INDEX ix_alertas_cirugia_estado              ON alertas (cirugia_id, estado);
CREATE INDEX ix_alertas_regla                       ON alertas (regla_seguridad_id);
CREATE INDEX ix_alertas_disparada                   ON alertas (disparada_en);
-- Para el tablero: alertas bloqueantes abiertas de una cirugía
CREATE INDEX ix_alertas_bloqueantes_abiertas
    ON alertas (cirugia_id) WHERE bloqueante AND estado IN ('ABIERTA', 'RECONOCIDA');

-- Solo un hito VIGENTE (no anulado) por tipo en cada cirugía
CREATE UNIQUE INDEX uq_hitos_vigentes
    ON hitos_cirugia (cirugia_id, tipo_hito) WHERE anulado_en IS NULL;
CREATE INDEX ix_hitos_ocurrido                      ON hitos_cirugia (ocurrido_en);

CREATE INDEX ix_eventos_cirugia_fecha               ON eventos_cirugia (cirugia_id, ocurrido_en);
CREATE INDEX ix_eventos_tipo                        ON eventos_cirugia (tipo_evento);
CREATE INDEX ix_eventos_actor_usuario               ON eventos_cirugia (actor_usuario_id);

CREATE INDEX ix_incidentes_cirugia                  ON incidentes (cirugia_id);
CREATE INDEX ix_incidentes_estado                   ON incidentes (estado);
CREATE INDEX ix_incidentes_reportado                ON incidentes (reportado_en);

CREATE INDEX ix_auditoria_entidad                   ON registros_auditoria (tipo_entidad, entidad_id);
CREATE INDEX ix_auditoria_usuario                   ON registros_auditoria (usuario_id);
CREATE INDEX ix_auditoria_fecha                     ON registros_auditoria (ocurrido_en);


-- =====================================================================
-- 4. FUNCIONES Y TRIGGERS DE REGLAS DE NEGOCIO
-- =====================================================================

-- ---------------------------------------------------------------------
-- 4.1 actualizado_en automático en toda tabla que tenga esa columna
-- ---------------------------------------------------------------------

CREATE FUNCTION fn_fijar_actualizado_en() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
  NEW.actualizado_en := localtimestamp;
RETURN NEW;
END;
$$;

DO $$
DECLARE
t text;
BEGIN
FOR t IN
SELECT c.table_name
FROM information_schema.columns c
         JOIN information_schema.tables tb
              ON tb.table_schema = c.table_schema AND tb.table_name = c.table_name
WHERE c.table_schema = current_schema()
  AND c.column_name = 'actualizado_en'
  AND tb.table_type = 'BASE TABLE'
    LOOP
    EXECUTE format(
      'CREATE TRIGGER tg_%1$s_actualizado_en BEFORE UPDATE ON %1$I
         FOR EACH ROW EXECUTE FUNCTION fn_fijar_actualizado_en()', t);
END LOOP;
END;
$$;

-- ---------------------------------------------------------------------
-- 4.2 Validación de asignaciones de personal
--     Regla 1  : el profesional debe tener el rol clínico del requerimiento.
--     Regla 9  : si el requerimiento exige especialidad, el profesional la tiene.
--     Cupo     : no más asignaciones vigentes que la cantidad solicitada.
--     Regla 4  : sin solape con otra cirugía vigente del mismo profesional.
--     Disponib.: no se asigna dentro de un bloque NO_DISPONIBLE.
--     Regla 17 : bloqueos para que dos coordinadores no asignen a la vez.
-- ---------------------------------------------------------------------

CREATE FUNCTION fn_validar_asignacion() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
v_req       requerimientos_roles_solicitud%ROWTYPE;
  v_rango     tsrange;
  v_ocupados  int;
  v_conflicto uuid;
BEGIN
  -- Solo se validan asignaciones vigentes
  IF NEW.estado NOT IN ('ASIGNADA', 'CONFIRMADA') THEN
    RETURN NEW;
END IF;

  -- Bloquea el requerimiento: serializa asignaciones al mismo cupo
SELECT * INTO v_req
FROM requerimientos_roles_solicitud
WHERE id = NEW.requerimiento_rol_id
    FOR UPDATE;

-- Mensaje claro; la FK compuesta fk_asignacion_requerimiento lo garantiza igual
IF v_req.id IS NULL OR v_req.solicitud_cirugia_id <> NEW.solicitud_cirugia_id THEN
    RAISE EXCEPTION 'El requerimiento % no pertenece a la solicitud de esta cirugía', NEW.requerimiento_rol_id
      USING ERRCODE = 'foreign_key_violation';
END IF;

SELECT tsrange(inicio_programado, fin_programado, '[)') INTO v_rango
FROM cirugias WHERE id = NEW.cirugia_id;

-- Profesional activo
IF NOT EXISTS (SELECT 1 FROM perfiles_profesionales
                 WHERE id = NEW.profesional_id AND activo) THEN
    RAISE EXCEPTION 'El profesional % no está activo', NEW.profesional_id
      USING ERRCODE = 'check_violation';
END IF;

  -- Regla 1
  IF NOT EXISTS (SELECT 1 FROM profesional_roles_clinicos
                 WHERE profesional_id = NEW.profesional_id
                   AND rol_clinico_id = v_req.rol_clinico_id) THEN
    RAISE EXCEPTION 'El profesional % no tiene registrado el rol clínico requerido', NEW.profesional_id
      USING ERRCODE = 'check_violation';
END IF;

  -- Regla 9
  IF v_req.especialidad_id IS NOT NULL
     AND NOT EXISTS (SELECT 1 FROM profesional_especialidades
                     WHERE profesional_id = NEW.profesional_id
                       AND especialidad_id = v_req.especialidad_id) THEN
    RAISE EXCEPTION 'El profesional % no tiene la especialidad exigida por el requerimiento', NEW.profesional_id
      USING ERRCODE = 'check_violation';
END IF;

  -- Cupo del requerimiento
SELECT count(*) INTO v_ocupados
FROM asignaciones_personal_cirugia
WHERE requerimiento_rol_id = NEW.requerimiento_rol_id
  AND estado IN ('ASIGNADA', 'CONFIRMADA')
  AND id <> NEW.id;

IF v_ocupados >= v_req.cantidad THEN
    RAISE EXCEPTION 'El requerimiento ya está cubierto (% de %)', v_ocupados, v_req.cantidad
      USING ERRCODE = 'check_violation';
END IF;

  -- Regla 17: bloqueo por profesional durante la transacción
  PERFORM pg_advisory_xact_lock(hashtextextended('profesional:' || NEW.profesional_id::text, 0));

  -- Regla 4: solape con otra cirugía vigente
SELECT c.id INTO v_conflicto
FROM asignaciones_personal_cirugia a
         JOIN cirugias c ON c.id = a.cirugia_id
WHERE a.profesional_id = NEW.profesional_id
  AND a.id <> NEW.id
  AND a.cirugia_id <> NEW.cirugia_id
  AND a.estado IN ('ASIGNADA', 'CONFIRMADA')
  AND c.estado NOT IN ('CANCELADA', 'SUSPENDIDA')
  AND tsrange(c.inicio_programado, c.fin_programado, '[)') && v_rango
  LIMIT 1;

IF v_conflicto IS NOT NULL THEN
    RAISE EXCEPTION 'El profesional % ya está asignado a la cirugía % en un horario que se solapa',
                    NEW.profesional_id, v_conflicto
      USING ERRCODE = 'exclusion_violation';
END IF;

  -- Disponibilidad explícita
  IF EXISTS (SELECT 1 FROM disponibilidad_profesional
             WHERE profesional_id = NEW.profesional_id
               AND tipo_disponibilidad = 'NO_DISPONIBLE'
               AND tsrange(inicia_en, termina_en, '[)') && v_rango) THEN
    RAISE EXCEPTION 'El profesional % está marcado como NO_DISPONIBLE en ese horario', NEW.profesional_id
      USING ERRCODE = 'check_violation';
END IF;

RETURN NEW;
END;
$$;

CREATE TRIGGER tg_asignaciones_validar
    BEFORE INSERT OR UPDATE OF estado, profesional_id, requerimiento_rol_id, cirugia_id
                     ON asignaciones_personal_cirugia
                         FOR EACH ROW EXECUTE FUNCTION fn_validar_asignacion();

-- ---------------------------------------------------------------------
-- 4.3 Si cambia el horario (o se reactiva) una cirugía, se re-valida que
--     su personal no quede solapado con otras cirugías.
-- ---------------------------------------------------------------------

CREATE FUNCTION fn_revalidar_personal_cirugia() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
r           record;
  v_rango     tsrange := tsrange(NEW.inicio_programado, NEW.fin_programado, '[)');
  v_conflicto uuid;
BEGIN
  IF NEW.estado IN ('CANCELADA', 'SUSPENDIDA') THEN
    RETURN NEW;
END IF;

FOR r IN
SELECT profesional_id
FROM asignaciones_personal_cirugia
WHERE cirugia_id = NEW.id AND estado IN ('ASIGNADA', 'CONFIRMADA')
ORDER BY profesional_id          -- orden fijo para evitar interbloqueos
    LOOP
    PERFORM pg_advisory_xact_lock(hashtextextended('profesional:' || r.profesional_id::text, 0));

SELECT c.id INTO v_conflicto
FROM asignaciones_personal_cirugia a
         JOIN cirugias c ON c.id = a.cirugia_id
WHERE a.profesional_id = r.profesional_id
  AND a.cirugia_id <> NEW.id
  AND a.estado IN ('ASIGNADA', 'CONFIRMADA')
  AND c.estado NOT IN ('CANCELADA', 'SUSPENDIDA')
  AND tsrange(c.inicio_programado, c.fin_programado, '[)') && v_rango
    LIMIT 1;

IF v_conflicto IS NOT NULL THEN
      RAISE EXCEPTION 'El nuevo horario solapa al profesional % con la cirugía %',
                      r.profesional_id, v_conflicto
        USING ERRCODE = 'exclusion_violation';
END IF;
END LOOP;

RETURN NEW;
END;
$$;

CREATE TRIGGER tg_cirugias_revalidar_personal
    AFTER UPDATE OF inicio_programado, fin_programado, estado
    ON cirugias
    FOR EACH ROW EXECUTE FUNCTION fn_revalidar_personal_cirugia();

-- ---------------------------------------------------------------------
-- 4.4 Regla 10: plantillas publicadas o retiradas no se modifican.
--     Solo se permite PUBLICADA → RETIRADA.
-- ---------------------------------------------------------------------

CREATE FUNCTION fn_proteger_plantilla() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    IF OLD.estado <> 'BORRADOR' THEN
      RAISE EXCEPTION 'No se puede borrar la plantilla % v%: ya fue publicada', OLD.codigo, OLD.version;
END IF;
RETURN OLD;
END IF;

  IF OLD.estado = 'BORRADOR' THEN
    RETURN NEW;
END IF;

  IF (NEW.codigo, NEW.nombre, NEW.descripcion, NEW.version, NEW.publicada_en)
     IS DISTINCT FROM
     (OLD.codigo, OLD.nombre, OLD.descripcion, OLD.version, OLD.publicada_en) THEN
    RAISE EXCEPTION 'La plantilla % v% está %: cree una nueva versión', OLD.codigo, OLD.version, OLD.estado;
END IF;

  IF NEW.estado <> OLD.estado
     AND NOT (OLD.estado = 'PUBLICADA' AND NEW.estado = 'RETIRADA') THEN
    RAISE EXCEPTION 'Transición de estado no permitida: % → %', OLD.estado, NEW.estado;
END IF;

RETURN NEW;
END;
$$;

CREATE TRIGGER tg_plantillas_proteger
    BEFORE UPDATE OR DELETE ON plantillas_checklist
  FOR EACH ROW EXECUTE FUNCTION fn_proteger_plantilla();

CREATE FUNCTION fn_proteger_contenido_plantilla() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
v_plantilla_id uuid;
  v_estado       estado_plantilla;
BEGIN
  IF TG_TABLE_NAME = 'fases_plantilla_checklist' THEN
    v_plantilla_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.plantilla_id ELSE NEW.plantilla_id END;
SELECT estado INTO v_estado FROM plantillas_checklist WHERE id = v_plantilla_id;
-- Si se mueve una fase a otra plantilla, la de origen también debe ser borrador
IF TG_OP = 'UPDATE' AND OLD.plantilla_id <> NEW.plantilla_id THEN
      IF (SELECT estado FROM plantillas_checklist WHERE id = OLD.plantilla_id) <> 'BORRADOR' THEN
        v_estado := 'PUBLICADA';
END IF;
END IF;
ELSE  -- items_plantilla_checklist
SELECT p.estado INTO v_estado
FROM fases_plantilla_checklist f
         JOIN plantillas_checklist p ON p.id = f.plantilla_id
WHERE f.id = CASE WHEN TG_OP = 'DELETE' THEN OLD.fase_id ELSE NEW.fase_id END;
IF TG_OP = 'UPDATE' AND OLD.fase_id <> NEW.fase_id THEN
      IF (SELECT p.estado FROM fases_plantilla_checklist f
          JOIN plantillas_checklist p ON p.id = f.plantilla_id
          WHERE f.id = OLD.fase_id) <> 'BORRADOR' THEN
        v_estado := 'PUBLICADA';
END IF;
END IF;
END IF;

  IF v_estado <> 'BORRADOR' THEN
    RAISE EXCEPTION 'No se puede modificar el contenido de una plantilla %: cree una nueva versión', v_estado;
END IF;

RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

CREATE TRIGGER tg_fases_plantilla_proteger
    BEFORE INSERT OR UPDATE OR DELETE ON fases_plantilla_checklist
    FOR EACH ROW EXECUTE FUNCTION fn_proteger_contenido_plantilla();

CREATE TRIGGER tg_items_plantilla_proteger
    BEFORE INSERT OR UPDATE OR DELETE ON items_plantilla_checklist
    FOR EACH ROW EXECUTE FUNCTION fn_proteger_contenido_plantilla();

-- ---------------------------------------------------------------------
-- 4.5 Regla 12: tablas solo-agregar (no UPDATE, no DELETE)
-- ---------------------------------------------------------------------

CREATE FUNCTION fn_solo_agregar() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'La tabla % es solo-agregar: no admite %', TG_TABLE_NAME, TG_OP;
END;
$$;

CREATE TRIGGER tg_eventos_solo_agregar
    BEFORE UPDATE OR DELETE ON eventos_cirugia
  FOR EACH ROW EXECUTE FUNCTION fn_solo_agregar();

CREATE TRIGGER tg_auditoria_solo_agregar
    BEFORE UPDATE OR DELETE ON registros_auditoria
  FOR EACH ROW EXECUTE FUNCTION fn_solo_agregar();

-- ---------------------------------------------------------------------
-- 4.6 Hitos: no se borran; lo único que se puede hacer es anularlos
--     una vez (anulado_en, anulado_por, motivo). ocurrido_en nunca cambia.
-- ---------------------------------------------------------------------

CREATE FUNCTION fn_proteger_hito() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'Los hitos no se borran: anúlelo y registre uno nuevo con corrige_hito_id';
END IF;

  IF OLD.anulado_en IS NOT NULL THEN
    RAISE EXCEPTION 'El hito % ya fue anulado y no admite cambios', OLD.id;
END IF;

  IF (NEW.cirugia_id, NEW.tipo_hito, NEW.ocurrido_en, NEW.registrado_por_asignacion_id,
      NEW.notas, NEW.corrige_hito_id, NEW.creado_en)
     IS DISTINCT FROM
     (OLD.cirugia_id, OLD.tipo_hito, OLD.ocurrido_en, OLD.registrado_por_asignacion_id,
      OLD.notas, OLD.corrige_hito_id, OLD.creado_en) THEN
    RAISE EXCEPTION 'Un hito no se edita: solo se puede anular (anulado_en, anulado_por_usuario_id, motivo_anulacion)';
END IF;

RETURN NEW;
END;
$$;

CREATE TRIGGER tg_hitos_proteger
    BEFORE UPDATE OR DELETE ON hitos_cirugia
  FOR EACH ROW EXECUTE FUNCTION fn_proteger_hito();

-- ---------------------------------------------------------------------
-- 4.7 Personal VIGENTE de la misma cirugía.
--     Las FKs compuestas (x_asignacion_id, cirugia_id) ya garantizan que la
--     asignación es de ESA cirugía. Este trigger añade que siga vigente
--     (ASIGNADA o CONFIRMADA): alguien cancelado no registra ni confirma.
-- ---------------------------------------------------------------------

CREATE FUNCTION fn_exigir_asignacion_vigente(p_asignacion_id uuid, p_que text)
    RETURNS void
    LANGUAGE plpgsql AS $$
BEGIN
  IF p_asignacion_id IS NOT NULL
     AND NOT EXISTS (SELECT 1 FROM asignaciones_personal_cirugia
                     WHERE id = p_asignacion_id AND estado IN ('ASIGNADA', 'CONFIRMADA')) THEN
    RAISE EXCEPTION '%: la asignación % no está vigente (rechazada o cancelada)', p_que, p_asignacion_id
      USING ERRCODE = 'check_violation';
END IF;
END;
$$;

CREATE FUNCTION fn_validar_personal_vigente() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
CASE TG_TABLE_NAME
    WHEN 'fases_checklist_cirugia' THEN
      PERFORM fn_exigir_asignacion_vigente(NEW.cerrada_por_asignacion_id, 'Cierre de fase');
WHEN 'items_checklist_cirugia' THEN
      PERFORM fn_exigir_asignacion_vigente(NEW.registrado_por_asignacion_id, 'Registro de ítem');
WHEN 'confirmaciones_item_checklist' THEN
      PERFORM fn_exigir_asignacion_vigente(NEW.asignacion_personal_id, 'Confirmación de ítem');
WHEN 'confirmaciones_recuento' THEN
      PERFORM fn_exigir_asignacion_vigente(NEW.asignacion_personal_id, 'Confirmación de recuento');
WHEN 'recuentos_cirugia' THEN
      PERFORM fn_exigir_asignacion_vigente(NEW.registrado_inicial_por_asignacion_id, 'Registro de recuento inicial');
      PERFORM fn_exigir_asignacion_vigente(NEW.registrado_final_por_asignacion_id, 'Registro de recuento final');
WHEN 'sets_instrumentales_cirugia', 'instrumentos_cirugia' THEN
      PERFORM fn_exigir_asignacion_vigente(NEW.preparado_por_asignacion_id, 'Preparación de instrumental');
WHEN 'hitos_cirugia' THEN
      PERFORM fn_exigir_asignacion_vigente(NEW.registrado_por_asignacion_id, 'Registro de hito');
END CASE;
RETURN NEW;
END;
$$;

CREATE TRIGGER tg_fases_cirugia_personal
    BEFORE INSERT OR UPDATE OF cerrada_por_asignacion_id ON fases_checklist_cirugia
    FOR EACH ROW EXECUTE FUNCTION fn_validar_personal_vigente();

CREATE TRIGGER tg_items_cirugia_personal
    BEFORE INSERT OR UPDATE OF registrado_por_asignacion_id ON items_checklist_cirugia
    FOR EACH ROW EXECUTE FUNCTION fn_validar_personal_vigente();

CREATE TRIGGER tg_confirmaciones_item_personal
    BEFORE INSERT OR UPDATE ON confirmaciones_item_checklist
                         FOR EACH ROW EXECUTE FUNCTION fn_validar_personal_vigente();

CREATE TRIGGER tg_confirmaciones_recuento_personal
    BEFORE INSERT OR UPDATE ON confirmaciones_recuento
                         FOR EACH ROW EXECUTE FUNCTION fn_validar_personal_vigente();

CREATE TRIGGER tg_recuentos_personal
    BEFORE INSERT OR UPDATE OF registrado_inicial_por_asignacion_id, registrado_final_por_asignacion_id
                     ON recuentos_cirugia
                         FOR EACH ROW EXECUTE FUNCTION fn_validar_personal_vigente();

CREATE TRIGGER tg_sets_cirugia_personal
    BEFORE INSERT OR UPDATE OF preparado_por_asignacion_id ON sets_instrumentales_cirugia
    FOR EACH ROW EXECUTE FUNCTION fn_validar_personal_vigente();

CREATE TRIGGER tg_instrumentos_cirugia_personal
    BEFORE INSERT OR UPDATE OF preparado_por_asignacion_id ON instrumentos_cirugia
    FOR EACH ROW EXECUTE FUNCTION fn_validar_personal_vigente();

CREATE TRIGGER tg_hitos_personal
    BEFORE INSERT ON hitos_cirugia
    FOR EACH ROW EXECUTE FUNCTION fn_validar_personal_vigente();

-- ---------------------------------------------------------------------
-- 4.8 V4: quien confirma un ítem debe cubrir, EN ESTA CIRUGÍA, el rol
--     clínico responsable del ítem (si el ítem lo define).
--     Se usa el rol del requerimiento que cubre la asignación (la calidad en
--     la que participa), no todos los roles que la persona tenga registrados.
-- ---------------------------------------------------------------------

CREATE FUNCTION fn_validar_rol_confirmacion() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
v_rol_item  uuid;
  v_rol_asig  uuid;
BEGIN
SELECT rol_clinico_responsable_id INTO v_rol_item
FROM items_checklist_cirugia WHERE id = NEW.item_checklist_cirugia_id;

IF v_rol_item IS NULL THEN
    RETURN NEW;
END IF;

SELECT r.rol_clinico_id INTO v_rol_asig
FROM asignaciones_personal_cirugia a
         JOIN requerimientos_roles_solicitud r ON r.id = a.requerimiento_rol_id
WHERE a.id = NEW.asignacion_personal_id;

IF v_rol_asig IS DISTINCT FROM v_rol_item THEN
    RAISE EXCEPTION 'Confirmación rechazada: el ítem exige el rol %, pero la persona participa como %',
      (SELECT codigo FROM roles_clinicos WHERE id = v_rol_item),
      (SELECT codigo FROM roles_clinicos WHERE id = v_rol_asig)
      USING ERRCODE = 'check_violation';
END IF;

RETURN NEW;
END;
$$;

CREATE TRIGGER tg_confirmaciones_item_rol
    BEFORE INSERT OR UPDATE ON confirmaciones_item_checklist
                         FOR EACH ROW EXECUTE FUNCTION fn_validar_rol_confirmacion();

-- ---------------------------------------------------------------------
-- 4.9 V4: no iniciar el tablero sin operador.
--     "Iniciar tablero" = el checklist pasa a EN_PROGRESO / recibe iniciado_en,
--     o se registra el hito TABLERO_INICIADO.
-- ---------------------------------------------------------------------

CREATE FUNCTION fn_exigir_operador(p_cirugia_id uuid) RETURNS void
    LANGUAGE plpgsql AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM asignaciones_personal_cirugia
                 WHERE cirugia_id = p_cirugia_id
                   AND es_operador_tablero
                   AND estado IN ('ASIGNADA', 'CONFIRMADA')) THEN
    RAISE EXCEPTION 'No se puede iniciar el tablero de la cirugía %: no hay operador del tablero asignado', p_cirugia_id
      USING ERRCODE = 'check_violation';
END IF;
END;
$$;

CREATE FUNCTION fn_validar_inicio_tablero() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
  IF TG_TABLE_NAME = 'checklists_cirugia' THEN
    IF (NEW.estado <> 'PENDIENTE' OR NEW.iniciado_en IS NOT NULL)
       AND (TG_OP = 'INSERT' OR (OLD.estado = 'PENDIENTE' AND OLD.iniciado_en IS NULL)) THEN
      PERFORM fn_exigir_operador(NEW.cirugia_id);
END IF;
  ELSIF TG_TABLE_NAME = 'hitos_cirugia' THEN
    IF NEW.tipo_hito = 'TABLERO_INICIADO' THEN
      PERFORM fn_exigir_operador(NEW.cirugia_id);
END IF;
END IF;
RETURN NEW;
END;
$$;

CREATE TRIGGER tg_checklists_inicio_tablero
    BEFORE INSERT OR UPDATE OF estado, iniciado_en ON checklists_cirugia
    FOR EACH ROW EXECUTE FUNCTION fn_validar_inicio_tablero();

CREATE TRIGGER tg_hitos_inicio_tablero
    BEFORE INSERT ON hitos_cirugia
    FOR EACH ROW EXECUTE FUNCTION fn_validar_inicio_tablero();

-- ---------------------------------------------------------------------
-- 4.10 V4: no iniciar la cirugía si falta instrumental obligatorio.
--      "Iniciar cirugía" = estado pasa a EN_CIRUGIA o hito CIRUGIA_INICIADA.
--      Se revisan los sets y los instrumentos directos (set_cirugia_id NULL)
--      marcados es_requerido con cantidad_preparada < cantidad_requerida.
--      Excepción (regla 16): una alerta de la regla
--      INSTRUMENTAL_REQUERIDO_INCOMPLETO con excepción autorizada.
-- ---------------------------------------------------------------------

CREATE FUNCTION fn_exigir_instrumental_completo(p_cirugia_id uuid) RETURNS void
    LANGUAGE plpgsql AS $$
DECLARE
v_faltantes text;
BEGIN
SELECT string_agg(nombre, ', ') INTO v_faltantes
FROM (
         SELECT nombre_set AS nombre FROM sets_instrumentales_cirugia
         WHERE cirugia_id = p_cirugia_id AND es_requerido AND cantidad_preparada < cantidad_requerida
         UNION ALL
         SELECT nombre_instrumento FROM instrumentos_cirugia
         WHERE cirugia_id = p_cirugia_id AND set_cirugia_id IS NULL
           AND es_requerido AND cantidad_preparada < cantidad_requerida
     ) f;

IF v_faltantes IS NULL THEN
    RETURN;
END IF;

  IF EXISTS (SELECT 1 FROM alertas a
             JOIN reglas_seguridad r ON r.id = a.regla_seguridad_id
             WHERE a.cirugia_id = p_cirugia_id
               AND r.codigo = 'INSTRUMENTAL_REQUERIDO_INCOMPLETO'
               AND a.excepcion_autorizada_por_usuario_id IS NOT NULL) THEN
    RETURN;   -- continuar fue autorizado explícitamente
END IF;

  RAISE EXCEPTION 'No se puede iniciar la cirugía %: falta instrumental obligatorio (%)', p_cirugia_id, v_faltantes
    USING ERRCODE = 'check_violation';
END;
$$;

CREATE FUNCTION fn_validar_inicio_cirugia() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
  IF TG_TABLE_NAME = 'cirugias' THEN
    IF NEW.estado = 'EN_CIRUGIA' AND OLD.estado IS DISTINCT FROM 'EN_CIRUGIA' THEN
      PERFORM fn_exigir_instrumental_completo(NEW.id);
END IF;
  ELSIF NEW.tipo_hito = 'CIRUGIA_INICIADA' THEN
    PERFORM fn_exigir_instrumental_completo(NEW.cirugia_id);
END IF;
RETURN NEW;
END;
$$;

CREATE TRIGGER tg_cirugias_inicio_instrumental
    BEFORE UPDATE OF estado ON cirugias
    FOR EACH ROW EXECUTE FUNCTION fn_validar_inicio_cirugia();

CREATE TRIGGER tg_hitos_inicio_instrumental
    BEFORE INSERT ON hitos_cirugia
    FOR EACH ROW EXECUTE FUNCTION fn_validar_inicio_cirugia();

-- ---------------------------------------------------------------------
-- 4.11 V4: no completar una fase con alertas bloqueantes abiertas
--      (ABIERTA o RECONOCIDA) de la cirugía, salvo excepción autorizada.
-- ---------------------------------------------------------------------

CREATE FUNCTION fn_validar_cierre_fase() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
v_alertas text;
BEGIN
  IF NEW.estado = 'COMPLETADA' AND (TG_OP = 'INSERT' OR OLD.estado <> 'COMPLETADA') THEN
SELECT string_agg(titulo, '; ') INTO v_alertas
FROM alertas
WHERE cirugia_id = NEW.cirugia_id
  AND bloqueante
  AND estado IN ('ABIERTA', 'RECONOCIDA')
  AND excepcion_autorizada_por_usuario_id IS NULL;

IF v_alertas IS NOT NULL THEN
      RAISE EXCEPTION 'No se puede completar la fase %: hay alertas bloqueantes abiertas (%)', NEW.codigo_fase, v_alertas
        USING ERRCODE = 'check_violation';
END IF;
END IF;
RETURN NEW;
END;
$$;

CREATE TRIGGER tg_fases_cierre_alertas
    BEFORE INSERT OR UPDATE OF estado ON fases_checklist_cirugia
    FOR EACH ROW EXECUTE FUNCTION fn_validar_cierre_fase();

-- ---------------------------------------------------------------------
-- 4.12 V4: los registros clínicos de una cirugía no se borran físicamente.
--      (Catálogos: las FKs ya impiden borrar lo que tiene histórico; se
--      desactivan con activo = false.)
--      Nota: TRUNCATE no dispara triggers; sirve para reiniciar una base
--      de desarrollo, nunca debe usarse en producción.
-- ---------------------------------------------------------------------

CREATE FUNCTION fn_sin_borrado() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Los registros de % son históricos y no se borran; use estados (CANCELADA, RESUELTA, anulación...)', TG_TABLE_NAME;
END;
$$;

DO $$
DECLARE
t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'cirugias', 'asignaciones_personal_cirugia', 'datos_preoperatorios_cirugia',
    'sets_instrumentales_cirugia', 'instrumentos_cirugia',
    'checklists_cirugia', 'fases_checklist_cirugia', 'items_checklist_cirugia',
    'confirmaciones_item_checklist', 'recuentos_cirugia', 'confirmaciones_recuento',
    'alertas', 'incidentes'
  ]
  LOOP
    EXECUTE format(
      'CREATE TRIGGER tg_%1$s_sin_borrado BEFORE DELETE ON %1$I
         FOR EACH ROW EXECUTE FUNCTION fn_sin_borrado()', t);
END LOOP;
END;
$$;

-- ---------------------------------------------------------------------
-- 4.13 fn_iniciar_checklist: copia (snapshot) una plantilla PUBLICADA a una
--     cirugía: checklist + fases + ítems. Devuelve el id del checklist.
--     Si p_plantilla_id es NULL usa la predeterminada del procedimiento.
-- ---------------------------------------------------------------------

CREATE FUNCTION fn_iniciar_checklist(p_cirugia_id uuid, p_plantilla_id uuid DEFAULT NULL)
    RETURNS uuid
    LANGUAGE plpgsql AS $$
DECLARE
v_plantilla  plantillas_checklist%ROWTYPE;
  v_checklist  uuid;
  v_fase       record;
  v_fase_nueva uuid;
BEGIN
  IF p_plantilla_id IS NULL THEN
SELECT ppc.plantilla_checklist_id INTO p_plantilla_id
FROM cirugias c
         JOIN solicitudes_cirugia s ON s.id = c.solicitud_cirugia_id
         JOIN procedimiento_plantillas_checklist ppc
              ON ppc.procedimiento_id = s.procedimiento_id AND ppc.es_predeterminada
WHERE c.id = p_cirugia_id;
END IF;

SELECT * INTO v_plantilla FROM plantillas_checklist WHERE id = p_plantilla_id;

IF v_plantilla.id IS NULL THEN
    RAISE EXCEPTION 'No hay plantilla para la cirugía % (ni indicada ni predeterminada)', p_cirugia_id;
END IF;
  IF v_plantilla.estado <> 'PUBLICADA' THEN
    RAISE EXCEPTION 'La plantilla % v% no está PUBLICADA', v_plantilla.codigo, v_plantilla.version;
END IF;
  IF EXISTS (SELECT 1 FROM checklists_cirugia WHERE cirugia_id = p_cirugia_id) THEN
    RAISE EXCEPTION 'La cirugía % ya tiene checklist', p_cirugia_id;
END IF;

INSERT INTO checklists_cirugia
(cirugia_id, plantilla_origen_id, codigo_plantilla, nombre_plantilla, version_plantilla)
VALUES
    (p_cirugia_id, v_plantilla.id, v_plantilla.codigo, v_plantilla.nombre, v_plantilla.version)
    RETURNING id INTO v_checklist;

FOR v_fase IN
SELECT * FROM fases_plantilla_checklist WHERE plantilla_id = v_plantilla.id ORDER BY orden
    LOOP
INSERT INTO fases_checklist_cirugia
(cirugia_id, checklist_cirugia_id, fase_plantilla_origen_id, codigo_fase, nombre_fase, orden)
VALUES
    (p_cirugia_id, v_checklist, v_fase.id, v_fase.codigo, v_fase.nombre, v_fase.orden)
    RETURNING id INTO v_fase_nueva;

INSERT INTO items_checklist_cirugia
(cirugia_id, fase_cirugia_id, item_plantilla_origen_id, codigo_item, etiqueta, descripcion,
 tipo_respuesta, obligatorio, bloqueante, rol_clinico_responsable_id, orden)
SELECT p_cirugia_id, v_fase_nueva, i.id, i.codigo, i.etiqueta, i.descripcion,
       i.tipo_respuesta, i.obligatorio, i.bloqueante, i.rol_clinico_responsable_id, i.orden
FROM items_plantilla_checklist i
WHERE i.fase_id = v_fase.id;
END LOOP;

RETURN v_checklist;
END;
$$;

-- ---------------------------------------------------------------------
-- 4.14 V4 fn_preparar_instrumental: copia (snapshot) el instrumental
--      predeterminado del procedimiento a la cirugía:
--        - cada set predeterminado  → sets_instrumentales_cirugia
--        - su composición           → instrumentos_cirugia (con set_cirugia_id),
--                                     cantidad = cantidad del set × cantidad en el set
--        - instrumentos directos    → instrumentos_cirugia (set_cirugia_id NULL)
--      Devuelve cuántos sets + instrumentos directos se copiaron.
-- ---------------------------------------------------------------------

CREATE FUNCTION fn_preparar_instrumental(p_cirugia_id uuid)
    RETURNS int
    LANGUAGE plpgsql AS $$
DECLARE
v_proc     uuid;
  v_set      record;
  v_set_cir  uuid;
  v_total    int := 0;
  v_directos int;
BEGIN
SELECT s.procedimiento_id INTO v_proc
FROM cirugias c JOIN solicitudes_cirugia s ON s.id = c.solicitud_cirugia_id
WHERE c.id = p_cirugia_id;

IF v_proc IS NULL THEN
    RAISE EXCEPTION 'Cirugía % no encontrada', p_cirugia_id;
END IF;
  IF EXISTS (SELECT 1 FROM sets_instrumentales_cirugia WHERE cirugia_id = p_cirugia_id)
     OR EXISTS (SELECT 1 FROM instrumentos_cirugia WHERE cirugia_id = p_cirugia_id) THEN
    RAISE EXCEPTION 'La cirugía % ya tiene instrumental preparado', p_cirugia_id;
END IF;

FOR v_set IN
SELECT sp.cantidad, sp.es_requerido, sp.notas, si.id, si.codigo, si.nombre
FROM sets_predeterminados_procedimiento sp
         JOIN sets_instrumentales si ON si.id = sp.set_instrumental_id
WHERE sp.procedimiento_id = v_proc
ORDER BY si.codigo
    LOOP
INSERT INTO sets_instrumentales_cirugia
(cirugia_id, set_origen_id, codigo_set, nombre_set, es_requerido, cantidad_requerida, notas)
VALUES
    (p_cirugia_id, v_set.id, v_set.codigo, v_set.nombre, v_set.es_requerido, v_set.cantidad, v_set.notas)
    RETURNING id INTO v_set_cir;

INSERT INTO instrumentos_cirugia
(cirugia_id, set_cirugia_id, instrumento_origen_id, codigo_instrumento, nombre_instrumento,
 es_requerido, cantidad_requerida)
SELECT p_cirugia_id, v_set_cir, iq.id, iq.codigo, iq.nombre,
       v_set.es_requerido, v_set.cantidad * ins.cantidad
FROM instrumentos_set ins
         JOIN instrumentos_quirurgicos iq ON iq.id = ins.instrumento_id
WHERE ins.set_instrumental_id = v_set.id;

v_total := v_total + 1;
END LOOP;

INSERT INTO instrumentos_cirugia
(cirugia_id, instrumento_origen_id, codigo_instrumento, nombre_instrumento,
 es_requerido, cantidad_requerida, notas)
SELECT p_cirugia_id, iq.id, iq.codigo, iq.nombre, ip.es_requerido, ip.cantidad, ip.notas
FROM instrumentos_predeterminados_procedimiento ip
         JOIN instrumentos_quirurgicos iq ON iq.id = ip.instrumento_id
WHERE ip.procedimiento_id = v_proc;
GET DIAGNOSTICS v_directos = ROW_COUNT;

RETURN v_total + v_directos;
END;
$$;

COMMIT;
