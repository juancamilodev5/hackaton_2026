-- =====================================================================
-- Semilla ESTRUCTURAL — Esquema V4
-- Datos que el sistema necesita para funcionar. Ejecutar después del esquema.
-- Es idempotente: se puede correr varias veces sin duplicar.
-- =====================================================================


-- ---------------------------------------------------------------------
-- ROLES DEL SISTEMA
-- ---------------------------------------------------------------------
INSERT INTO roles_sistema (codigo, nombre, descripcion) VALUES
                                                            ('ADMIN',               'Administrador',         'Configura catálogos, protocolos y usuarios'),
                                                            ('MEDICO',              'Médico',                'Crea solicitudes de cirugía'),
                                                            ('ENFERMERA_JEFE',      'Enfermera jefe',        'Programa cirugías y asigna personal'),
                                                            ('PERSONAL_QUIRURGICO', 'Personal quirúrgico',   'Participa en cirugías y opera el tablero'),
                                                            ('PACIENTE',            'Paciente',              'Acceso del paciente a su información'),
                                                            ('AUDITOR_CALIDAD',     'Auditor de calidad',    'Consulta indicadores, históricos y auditoría')
    ON CONFLICT (codigo) DO NOTHING;

-- ---------------------------------------------------------------------
-- PERMISOS
-- ---------------------------------------------------------------------
INSERT INTO permisos (codigo, nombre) VALUES
                                          ('USUARIOS_VER',              'Ver usuarios'),
                                          ('USUARIOS_GESTIONAR',        'Gestionar usuarios'),
                                          ('CATALOGOS_VER',             'Ver catálogos'),
                                          ('CATALOGOS_GESTIONAR',       'Gestionar catálogos'),
                                          ('INSTRUMENTAL_VER',          'Ver catálogo de instrumental'),
                                          ('INSTRUMENTAL_GESTIONAR',    'Gestionar catálogo de instrumental'),
                                          ('PREPARACION_QUIRURGICA_GESTIONAR', 'Gestionar preparación quirúrgica (instrumental de la cirugía)'),
                                          ('ALERTAS_GESTIONAR',         'Reconocer, resolver y autorizar excepciones de alertas'),
                                          ('PROTOCOLOS_VER',            'Ver protocolos de checklist'),
                                          ('PROTOCOLOS_GESTIONAR',      'Gestionar protocolos de checklist'),
                                          ('SOLICITUDES_CIRUGIA_VER',   'Ver solicitudes de cirugía'),
                                          ('SOLICITUDES_CIRUGIA_CREAR', 'Crear solicitudes de cirugía'),
                                          ('CIRUGIAS_VER',              'Ver cirugías'),
                                          ('CIRUGIAS_PROGRAMAR',        'Programar cirugías'),
                                          ('PERSONAL_ASIGNAR',          'Asignar personal a cirugías'),
                                          ('TABLERO_VER',               'Ver tablero de seguridad'),
                                          ('TABLERO_OPERAR',            'Operar tablero de seguridad'),
                                          ('CALIDAD_VER',               'Ver panel de calidad e indicadores'),
                                          ('AUDITORIA_VER',             'Ver registros de auditoría')
    ON CONFLICT (codigo) DO NOTHING;

-- ---------------------------------------------------------------------
-- PERMISOS POR ROL  (PROPUESTA: el modelo no los definía — revisar)
-- TABLERO_OPERAR habilita la función; además, en cada cirugía solo opera
-- quien tenga es_operador_tablero = true.
-- ---------------------------------------------------------------------
INSERT INTO rol_sistema_permisos (rol_sistema_id, permiso_id)
SELECT r.id, p.id
FROM (VALUES
          -- ADMIN: todo
          ('ADMIN', 'USUARIOS_VER'), ('ADMIN', 'USUARIOS_GESTIONAR'),
          ('ADMIN', 'CATALOGOS_VER'), ('ADMIN', 'CATALOGOS_GESTIONAR'),
          ('ADMIN', 'PROTOCOLOS_VER'), ('ADMIN', 'PROTOCOLOS_GESTIONAR'),
          ('ADMIN', 'SOLICITUDES_CIRUGIA_VER'), ('ADMIN', 'SOLICITUDES_CIRUGIA_CREAR'),
          ('ADMIN', 'CIRUGIAS_VER'), ('ADMIN', 'CIRUGIAS_PROGRAMAR'),
          ('ADMIN', 'PERSONAL_ASIGNAR'),
          ('ADMIN', 'TABLERO_VER'), ('ADMIN', 'TABLERO_OPERAR'),
          ('ADMIN', 'CALIDAD_VER'), ('ADMIN', 'AUDITORIA_VER'),
          ('ADMIN', 'INSTRUMENTAL_VER'), ('ADMIN', 'INSTRUMENTAL_GESTIONAR'),
          ('ADMIN', 'PREPARACION_QUIRURGICA_GESTIONAR'), ('ADMIN', 'ALERTAS_GESTIONAR'),
          -- MEDICO
          ('MEDICO', 'CATALOGOS_VER'), ('MEDICO', 'PROTOCOLOS_VER'),
          ('MEDICO', 'SOLICITUDES_CIRUGIA_VER'), ('MEDICO', 'SOLICITUDES_CIRUGIA_CREAR'),
          ('MEDICO', 'CIRUGIAS_VER'), ('MEDICO', 'TABLERO_VER'),
          ('MEDICO', 'INSTRUMENTAL_VER'), ('MEDICO', 'ALERTAS_GESTIONAR'),
          -- ENFERMERA_JEFE
          ('ENFERMERA_JEFE', 'CATALOGOS_VER'), ('ENFERMERA_JEFE', 'PROTOCOLOS_VER'),
          ('ENFERMERA_JEFE', 'SOLICITUDES_CIRUGIA_VER'),
          ('ENFERMERA_JEFE', 'CIRUGIAS_VER'), ('ENFERMERA_JEFE', 'CIRUGIAS_PROGRAMAR'),
          ('ENFERMERA_JEFE', 'PERSONAL_ASIGNAR'),
          ('ENFERMERA_JEFE', 'TABLERO_VER'), ('ENFERMERA_JEFE', 'TABLERO_OPERAR'),
          ('ENFERMERA_JEFE', 'CALIDAD_VER'),
          ('ENFERMERA_JEFE', 'INSTRUMENTAL_VER'), ('ENFERMERA_JEFE', 'PREPARACION_QUIRURGICA_GESTIONAR'),
          ('ENFERMERA_JEFE', 'ALERTAS_GESTIONAR'),
          -- PERSONAL_QUIRURGICO
          ('PERSONAL_QUIRURGICO', 'PROTOCOLOS_VER'), ('PERSONAL_QUIRURGICO', 'CIRUGIAS_VER'),
          ('PERSONAL_QUIRURGICO', 'TABLERO_VER'), ('PERSONAL_QUIRURGICO', 'TABLERO_OPERAR'),
          ('PERSONAL_QUIRURGICO', 'INSTRUMENTAL_VER'), ('PERSONAL_QUIRURGICO', 'PREPARACION_QUIRURGICA_GESTIONAR'),
          -- PACIENTE: sin permisos globales. Lo que vea de sí mismo se filtra en backend.
          -- AUDITOR_CALIDAD
          ('AUDITOR_CALIDAD', 'CATALOGOS_VER'), ('AUDITOR_CALIDAD', 'PROTOCOLOS_VER'),
          ('AUDITOR_CALIDAD', 'CIRUGIAS_VER'), ('AUDITOR_CALIDAD', 'TABLERO_VER'),
          ('AUDITOR_CALIDAD', 'CALIDAD_VER'), ('AUDITOR_CALIDAD', 'AUDITORIA_VER'),
          ('AUDITOR_CALIDAD', 'INSTRUMENTAL_VER')
     ) AS x (rol, permiso)
         JOIN roles_sistema r ON r.codigo = x.rol
         JOIN permisos      p ON p.codigo = x.permiso
    ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- ROLES CLÍNICOS
-- AUXILIAR_CIRCULANTE NO es automáticamente operador del tablero: eso se
-- define por cirugía en asignaciones_personal_cirugia.es_operador_tablero.
-- ---------------------------------------------------------------------
INSERT INTO roles_clinicos (codigo, nombre) VALUES
                                                ('CIRUJANO',                  'Cirujano'),
                                                ('ANESTESIOLOGO',             'Anestesiólogo'),
                                                ('INSTRUMENTADOR_QUIRURGICO', 'Instrumentador quirúrgico'),
                                                ('AYUDANTE_QUIRURGICO',       'Ayudante quirúrgico'),
                                                ('PERFUSIONISTA',             'Perfusionista'),
                                                ('AUXILIAR_CIRCULANTE',       'Auxiliar circulante'),
                                                ('ENFERMERO',                 'Enfermero'),
                                                ('AUXILIAR_ENFERMERIA',       'Auxiliar de enfermería')
    ON CONFLICT (codigo) DO NOTHING;

-- ---------------------------------------------------------------------
-- REGLAS DE SEGURIDAD
-- Venían definidas: RECUENTO_INCONSISTENTE (CRITICA, bloqueante) e
-- INSTRUMENTAL_REQUERIDO_INCOMPLETO (ALTA, bloqueante).
-- El resto de severidades son PROPUESTA — revisar.
-- La lógica de cada regla vive en el backend, identificada por codigo.
-- ---------------------------------------------------------------------
INSERT INTO reglas_seguridad (codigo, nombre, descripcion, severidad, bloqueante) VALUES
                                                                                      ('DATOS_OBLIGATORIOS_INCOMPLETOS', 'Datos obligatorios incompletos',
                                                                                       'Faltan datos preoperatorios obligatorios del paciente o del procedimiento', 'ALTA', false),
                                                                                      ('SITIO_QUIRURGICO_NO_CONFIRMADO', 'Sitio quirúrgico no confirmado',
                                                                                       'El sitio quirúrgico no ha sido confirmado antes de la incisión', 'CRITICA', true),
                                                                                      ('ALERGIA_NO_CONFIRMADA', 'Alergia no confirmada',
                                                                                       'El paciente tiene alergias registradas que no han sido confirmadas antes de la anestesia', 'ALTA', true),
                                                                                      ('ANTIBIOTICO_PENDIENTE', 'Antibiótico profiláctico pendiente',
                                                                                       'No se ha registrado la administración del antibiótico profiláctico antes de la incisión', 'ALTA', true),
                                                                                      ('EQUIPO_QUIRURGICO_INCOMPLETO', 'Equipo quirúrgico incompleto',
                                                                                       'Hay requerimientos de rol obligatorios sin cubrir', 'ALTA', true),
                                                                                      ('OPERADOR_TABLERO_NO_ASIGNADO', 'Operador del tablero no asignado',
                                                                                       'La cirugía no tiene una persona designada para operar el tablero', 'ALTA', true),
                                                                                      ('INSTRUMENTAL_REQUERIDO_INCOMPLETO', 'Instrumental requerido incompleto',
                                                                                       'Hay sets o instrumentos obligatorios sin preparar en la cantidad requerida', 'ALTA', true),
                                                                                      ('RECUENTO_INCONSISTENTE', 'Recuento inconsistente',
                                                                                       'La cantidad final no coincide con la inicial más la agregada', 'CRITICA', true),
                                                                                      ('FASE_OBLIGATORIA_INCOMPLETA', 'Fase obligatoria incompleta',
                                                                                       'Se intenta avanzar con ítems obligatorios pendientes en la fase', 'ALTA', true)
    ON CONFLICT (codigo) DO NOTHING;

-- ---------------------------------------------------------------------
-- PLANTILLA DE CHECKLIST: SEGURIDAD_QUIRURGICA_ESTANDAR v1
-- Se crea en BORRADOR, se cargan fases e ítems y al final se PUBLICA
-- (después de publicada, los triggers impiden modificarla).
-- Responsable, tipo de respuesta y bloqueante de cada ítem son PROPUESTA
-- basada en la lista de la OMS — revisar con el equipo clínico.
-- ---------------------------------------------------------------------
DO $$
DECLARE
v_plantilla uuid;
  v_fase      uuid;
BEGIN
  IF EXISTS (SELECT 1 FROM plantillas_checklist
             WHERE codigo = 'SEGURIDAD_QUIRURGICA_ESTANDAR' AND version = 1) THEN
    RAISE NOTICE 'Plantilla SEGURIDAD_QUIRURGICA_ESTANDAR v1 ya existe; se omite';
    RETURN;
END IF;

INSERT INTO plantillas_checklist (codigo, nombre, descripcion, version)
VALUES ('SEGURIDAD_QUIRURGICA_ESTANDAR', 'Seguridad quirúrgica estándar',
        'Lista de verificación basada en los tres momentos de la OMS', 1)
    RETURNING id INTO v_plantilla;

-- FASE 1: PREANESTESIA ------------------------------------------------
INSERT INTO fases_plantilla_checklist (plantilla_id, codigo, nombre, orden)
VALUES (v_plantilla, 'PREANESTESIA', 'Antes de la anestesia', 1)
    RETURNING id INTO v_fase;

INSERT INTO items_plantilla_checklist
(fase_id, codigo, etiqueta, tipo_respuesta, obligatorio, bloqueante, rol_clinico_responsable_id, orden)
SELECT v_fase, x.codigo, x.etiqueta, x.tipo, true, x.bloq, rc.id, x.orden
FROM (VALUES
          ('IDENTIDAD_PACIENTE',      'Identidad del paciente confirmada',  'CONFIRMACION', true,  'ANESTESIOLOGO', 1),
          ('PROCEDIMIENTO_CONFIRMADO','Procedimiento confirmado',           'CONFIRMACION', true,  'ANESTESIOLOGO', 2),
          ('SITIO_QUIRURGICO',        'Sitio quirúrgico verificado',        'CONFIRMACION', true,  'ANESTESIOLOGO', 3),
          ('ALERGIAS',                'Alergias verificadas',               'CONFIRMACION', true,  'ANESTESIOLOGO', 4),
          ('RIESGOS_RELEVANTES',      'Riesgos relevantes identificados',   'CONFIRMACION', false, 'ANESTESIOLOGO', 5),
          ('EQUIPAMIENTO_DISPONIBLE', 'Equipamiento disponible',            'CONFIRMACION', false, 'ANESTESIOLOGO', 6),
          ('INSTRUMENTAL_PREPARADO',  'Instrumental requerido preparado',   'CONFIRMACION', true,  'INSTRUMENTADOR_QUIRURGICO', 7)
     ) AS x (codigo, etiqueta, tipo, bloq, rol, orden)
         JOIN roles_clinicos rc ON rc.codigo = x.rol;

-- FASE 2: PREINCISION -------------------------------------------------
INSERT INTO fases_plantilla_checklist (plantilla_id, codigo, nombre, orden)
VALUES (v_plantilla, 'PREINCISION', 'Antes de la incisión', 2)
    RETURNING id INTO v_fase;

INSERT INTO items_plantilla_checklist
(fase_id, codigo, etiqueta, tipo_respuesta, obligatorio, bloqueante, rol_clinico_responsable_id, orden)
SELECT v_fase, x.codigo, x.etiqueta, x.tipo, true, x.bloq, rc.id, x.orden
FROM (VALUES
          ('PACIENTE_CONFIRMADO',      'Paciente confirmado',                     'CONFIRMACION', true,  'CIRUJANO',      1),
          ('PROCEDIMIENTO_CONFIRMADO', 'Procedimiento confirmado',                'CONFIRMACION', true,  'CIRUJANO',      2),
          ('SITIO_CONFIRMADO',         'Sitio quirúrgico confirmado',             'CONFIRMACION', true,  'CIRUJANO',      3),
          ('EQUIPO_CONFIRMADO',        'Equipo quirúrgico identificado',          'CONFIRMACION', false, 'CIRUJANO',      4),
          ('ANTIBIOTICO_PROFILACTICO', 'Antibiótico profiláctico administrado',   'CONFIRMACION', true,  'ANESTESIOLOGO', 5),
          ('RIESGOS_PREVISTOS',        'Riesgos previstos comunicados',           'CONFIRMACION', false, 'CIRUJANO',      6)
     ) AS x (codigo, etiqueta, tipo, bloq, rol, orden)
         JOIN roles_clinicos rc ON rc.codigo = x.rol;

-- FASE 3: SALIDA -------------------------------------------------------
INSERT INTO fases_plantilla_checklist (plantilla_id, codigo, nombre, orden)
VALUES (v_plantilla, 'SALIDA', 'Antes de salir del quirófano', 3)
    RETURNING id INTO v_fase;

INSERT INTO items_plantilla_checklist
(fase_id, codigo, etiqueta, tipo_respuesta, obligatorio, bloqueante, rol_clinico_responsable_id, orden)
SELECT v_fase, x.codigo, x.etiqueta, x.tipo, true, x.bloq, rc.id, x.orden
FROM (VALUES
          ('RECUENTO_INSTRUMENTAL',  'Recuento de instrumental correcto',  'CONFIRMACION', true,  'INSTRUMENTADOR_QUIRURGICO', 1),
          ('RECUENTO_GASAS',         'Recuento de gasas y material correcto', 'CONFIRMACION', true, 'INSTRUMENTADOR_QUIRURGICO', 2),
          ('RECUENTO_AGUJAS',        'Recuento de agujas correcto',        'CONFIRMACION', true,  'INSTRUMENTADOR_QUIRURGICO', 3),
          ('MUESTRAS_IDENTIFICADAS', 'Muestras identificadas',             'CONFIRMACION', false, 'AUXILIAR_CIRCULANTE',       4),
          ('NOVEDADES',              'Novedades registradas',              'TEXTO',        false, 'AUXILIAR_CIRCULANTE',       5),
          ('PROCEDIMIENTO_REALIZADO','Procedimiento realizado confirmado', 'CONFIRMACION', false, 'CIRUJANO',                  6)
     ) AS x (codigo, etiqueta, tipo, bloq, rol, orden)
         JOIN roles_clinicos rc ON rc.codigo = x.rol;

-- Publicar
UPDATE plantillas_checklist
SET estado = 'PUBLICADA', publicada_en = localtimestamp
WHERE id = v_plantilla;
END;
$$;

