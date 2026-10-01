-- =====================================================================
-- Datos DEMO — Esquema V4  ·  SOLO DESARROLLO
-- Ejecutar después del esquema y de la semilla estructural.
-- No es idempotente: correr una sola vez sobre una base limpia.
--
-- Deja lista una cirugía de bypass coronario para MAÑANA a las 07:00
-- en el quirófano cardiovascular, con su equipo asignado, Carlos Pérez
-- como operador del tablero y el checklist ya copiado desde la plantilla.
-- Los 2 ayudantes quirúrgicos quedan SIN asignar a propósito, para que
-- el tablero muestre "equipo incompleto".
-- Instrumental: se copia el predeterminado del bypass; solo el
-- SET_CARDIOVASCULAR_BASICO queda preparado, para que al intentar iniciar
-- la cirugía la base la bloquee por instrumental incompleto.
-- La composición de los sets es ILUSTRATIVA, no una lista clínica real.
-- =====================================================================


-- ---------------------------------------------------------------------
-- CATÁLOGOS DEMO
-- ---------------------------------------------------------------------
INSERT INTO especialidades (codigo, nombre) VALUES
                                                ('CIRUGIA_CARDIOVASCULAR', 'Cirugía cardiovascular'),
                                                ('CIRUGIA_GENERAL',        'Cirugía general'),
                                                ('ORTOPEDIA',              'Ortopedia'),
                                                ('NEUROCIRUGIA',           'Neurocirugía'),
                                                ('ANESTESIOLOGIA',         'Anestesiología');

INSERT INTO procedimientos_quirurgicos (codigo, nombre, duracion_estimada_minutos) VALUES
                                                                                       ('BYPASS_CORONARIO',             'Bypass coronario',              240),
                                                                                       ('REEMPLAZO_VALVULAR',           'Reemplazo valvular',            240),
                                                                                       ('COLECISTECTOMIA_LAPAROSCOPICA','Colecistectomía laparoscópica',  90),
                                                                                       ('ARTROSCOPIA_RODILLA',          'Artroscopia de rodilla',         60);

INSERT INTO procedimiento_especialidades (procedimiento_id, especialidad_id)
SELECT p.id, e.id
FROM (VALUES
          ('BYPASS_CORONARIO',              'CIRUGIA_CARDIOVASCULAR'),
          ('REEMPLAZO_VALVULAR',            'CIRUGIA_CARDIOVASCULAR'),
          ('COLECISTECTOMIA_LAPAROSCOPICA', 'CIRUGIA_GENERAL'),
          ('ARTROSCOPIA_RODILLA',           'ORTOPEDIA')
     ) AS x (proc, esp)
         JOIN procedimientos_quirurgicos p ON p.codigo = x.proc
         JOIN especialidades e             ON e.codigo = x.esp;

-- Roles predeterminados del bypass coronario
INSERT INTO roles_predeterminados_procedimiento (procedimiento_id, rol_clinico_id, cantidad_predeterminada)
SELECT p.id, rc.id, x.cant
FROM (VALUES
          ('CIRUJANO', 1), ('ANESTESIOLOGO', 1), ('INSTRUMENTADOR_QUIRURGICO', 1),
          ('AYUDANTE_QUIRURGICO', 2), ('PERFUSIONISTA', 1), ('AUXILIAR_CIRCULANTE', 1)
     ) AS x (rol, cant)
         JOIN roles_clinicos rc ON rc.codigo = x.rol
         CROSS JOIN procedimientos_quirurgicos p
WHERE p.codigo = 'BYPASS_CORONARIO';

-- Plantilla de checklist predeterminada para todos los procedimientos demo
INSERT INTO procedimiento_plantillas_checklist (procedimiento_id, plantilla_checklist_id, es_predeterminada)
SELECT p.id, t.id, true
FROM procedimientos_quirurgicos p
         CROSS JOIN plantillas_checklist t
WHERE t.codigo = 'SEGURIDAD_QUIRURGICA_ESTANDAR' AND t.version = 1;

-- ---------------------------------------------------------------------
-- INSTRUMENTAL DEMO
-- ---------------------------------------------------------------------
INSERT INTO instrumentos_quirurgicos (codigo, nombre) VALUES
                                                          ('PINZA_KELLY',        'Pinza Kelly'),
                                                          ('PINZA_MOSQUITO',     'Pinza mosquito'),
                                                          ('TIJERA_MAYO',        'Tijera Mayo'),
                                                          ('TIJERA_METZENBAUM',  'Tijera Metzenbaum'),
                                                          ('PORTAAGUJAS',        'Portaagujas'),
                                                          ('SEPARADOR_FARABEUF', 'Separador Farabeuf'),
                                                          ('SEPARADOR_ESTERNAL', 'Separador esternal');

INSERT INTO sets_instrumentales (codigo, nombre) VALUES
                                                     ('SET_CIRUGIA_BASICA',        'Set de cirugía básica'),
                                                     ('SET_CARDIOVASCULAR_BASICO', 'Set cardiovascular básico'),
                                                     ('SET_VASCULAR',              'Set vascular');

-- Composición: SET_CARDIOVASCULAR_BASICO viene del modelo; los otros dos son inventados para la demo
INSERT INTO instrumentos_set (set_instrumental_id, instrumento_id, cantidad)
SELECT s.id, i.id, x.cant
FROM (VALUES
          ('SET_CARDIOVASCULAR_BASICO', 'PINZA_KELLY',        2),
          ('SET_CARDIOVASCULAR_BASICO', 'PINZA_MOSQUITO',     4),
          ('SET_CARDIOVASCULAR_BASICO', 'TIJERA_MAYO',        2),
          ('SET_CARDIOVASCULAR_BASICO', 'PORTAAGUJAS',        2),
          ('SET_CIRUGIA_BASICA',        'PINZA_KELLY',        2),
          ('SET_CIRUGIA_BASICA',        'PINZA_MOSQUITO',     2),
          ('SET_CIRUGIA_BASICA',        'TIJERA_MAYO',        1),
          ('SET_CIRUGIA_BASICA',        'TIJERA_METZENBAUM',  1),
          ('SET_CIRUGIA_BASICA',        'PORTAAGUJAS',        1),
          ('SET_CIRUGIA_BASICA',        'SEPARADOR_FARABEUF', 2),
          ('SET_VASCULAR',              'PINZA_MOSQUITO',     4),
          ('SET_VASCULAR',              'TIJERA_METZENBAUM',  2),
          ('SET_VASCULAR',              'PORTAAGUJAS',        2)
     ) AS x (set_cod, instr, cant)
         JOIN sets_instrumentales s      ON s.codigo = x.set_cod
         JOIN instrumentos_quirurgicos i ON i.codigo = x.instr;

-- Instrumental predeterminado del bypass coronario
INSERT INTO sets_predeterminados_procedimiento (procedimiento_id, set_instrumental_id, cantidad)
SELECT p.id, s.id, 1
FROM procedimientos_quirurgicos p
         JOIN sets_instrumentales s ON s.codigo IN ('SET_CARDIOVASCULAR_BASICO', 'SET_VASCULAR')
WHERE p.codigo = 'BYPASS_CORONARIO';

INSERT INTO instrumentos_predeterminados_procedimiento (procedimiento_id, instrumento_id, cantidad)
SELECT p.id, i.id, 1
FROM procedimientos_quirurgicos p
         JOIN instrumentos_quirurgicos i ON i.codigo = 'SEPARADOR_ESTERNAL'
WHERE p.codigo = 'BYPASS_CORONARIO';

INSERT INTO quirofanos (codigo, nombre) VALUES
                                            ('QUIROFANO_01',             'Quirófano 01'),
                                            ('QUIROFANO_02',             'Quirófano 02'),
                                            ('QUIROFANO_CARDIOVASCULAR', 'Quirófano cardiovascular');

-- ---------------------------------------------------------------------
-- PERSONAS DEMO
-- La enfermera jefe no venía en la lista; se agrega porque alguien debe
-- programar la cirugía y asignar el personal.
-- ---------------------------------------------------------------------
INSERT INTO usuarios (nombres, apellidos, correo) VALUES
                                                      ('Alejandro',   'Martínez',  'alejandro.martinez@demo.local'),
                                                      ('Laura',       'Rodríguez', 'laura.rodriguez@demo.local'),
                                                      ('María',       'Fernández', 'maria.fernandez@demo.local'),
                                                      ('Carlos',      'Pérez',     'carlos.perez@demo.local'),
                                                      ('Andrés',      'Gómez',     'andres.gomez@demo.local'),
                                                      ('Enfermera',   'Jefe Demo', 'enfermera.jefe@demo.local'),
                                                      ('Carlos Andrés','Mendoza',  'carlos.mendoza@demo.local');

-- Roles del sistema
INSERT INTO usuario_roles_sistema (usuario_id, rol_sistema_id)
SELECT u.id, r.id
FROM (VALUES
          ('alejandro.martinez@demo.local', 'MEDICO'),
          ('laura.rodriguez@demo.local',    'MEDICO'),
          ('maria.fernandez@demo.local',    'PERSONAL_QUIRURGICO'),
          ('carlos.perez@demo.local',       'PERSONAL_QUIRURGICO'),
          ('andres.gomez@demo.local',       'PERSONAL_QUIRURGICO'),
          ('enfermera.jefe@demo.local',     'ENFERMERA_JEFE'),
          ('carlos.mendoza@demo.local',     'PACIENTE')
     ) AS x (correo, rol)
         JOIN usuarios u      ON u.correo = x.correo
         JOIN roles_sistema r ON r.codigo = x.rol;

-- Perfiles profesionales
INSERT INTO perfiles_profesionales (usuario_id)
SELECT id FROM usuarios
WHERE correo IN ('alejandro.martinez@demo.local', 'laura.rodriguez@demo.local',
                 'maria.fernandez@demo.local', 'carlos.perez@demo.local',
                 'andres.gomez@demo.local');

INSERT INTO profesional_roles_clinicos (profesional_id, rol_clinico_id)
SELECT pp.id, rc.id
FROM (VALUES
          ('alejandro.martinez@demo.local', 'CIRUJANO'),
          ('laura.rodriguez@demo.local',    'ANESTESIOLOGO'),
          ('maria.fernandez@demo.local',    'INSTRUMENTADOR_QUIRURGICO'),
          ('carlos.perez@demo.local',       'AUXILIAR_CIRCULANTE'),
          ('andres.gomez@demo.local',       'PERFUSIONISTA')
     ) AS x (correo, rol)
         JOIN usuarios u                ON u.correo = x.correo
         JOIN perfiles_profesionales pp ON pp.usuario_id = u.id
         JOIN roles_clinicos rc         ON rc.codigo = x.rol;

INSERT INTO profesional_especialidades (profesional_id, especialidad_id)
SELECT pp.id, e.id
FROM (VALUES
          ('alejandro.martinez@demo.local', 'CIRUGIA_CARDIOVASCULAR'),
          ('laura.rodriguez@demo.local',    'ANESTESIOLOGIA')
     ) AS x (correo, esp)
         JOIN usuarios u                ON u.correo = x.correo
         JOIN perfiles_profesionales pp ON pp.usuario_id = u.id
         JOIN especialidades e          ON e.codigo = x.esp;

-- Paciente (con cuenta) y su alergia
INSERT INTO pacientes (usuario_id, nombres, apellidos, tipo_documento, numero_documento, fecha_nacimiento)
SELECT id, 'Carlos Andrés', 'Mendoza', 'CC', '1000000001', DATE '1962-04-18'
FROM usuarios WHERE correo = 'carlos.mendoza@demo.local';

INSERT INTO alergias_paciente (paciente_id, sustancia, reaccion, severidad)
SELECT id, 'Penicilina', 'Urticaria', 'MODERADA'
FROM pacientes WHERE numero_documento = '1000000001';

-- ---------------------------------------------------------------------
-- CIRUGÍA DEMO
-- ---------------------------------------------------------------------
DO $$
DECLARE
v_paciente    uuid := (SELECT id FROM pacientes WHERE numero_documento = '1000000001');
  v_medico      uuid := (SELECT pp.id FROM perfiles_profesionales pp JOIN usuarios u ON u.id = pp.usuario_id
                         WHERE u.correo = 'alejandro.martinez@demo.local');
  v_jefe        uuid := (SELECT id FROM usuarios WHERE correo = 'enfermera.jefe@demo.local');
  v_esp         uuid := (SELECT id FROM especialidades WHERE codigo = 'CIRUGIA_CARDIOVASCULAR');
  v_proc        uuid := (SELECT id FROM procedimientos_quirurgicos WHERE codigo = 'BYPASS_CORONARIO');
  v_quirofano   uuid := (SELECT id FROM quirofanos WHERE codigo = 'QUIROFANO_CARDIOVASCULAR');
  v_solicitud   uuid;
  v_cirugia     uuid;
  v_inicio      timestamp := (current_date + 1) + time '07:00';
BEGIN
  -- Solicitud del médico
INSERT INTO solicitudes_cirugia
(paciente_id, medico_solicitante_id, especialidad_solicitante_id, procedimiento_id,
 sitio_quirurgico, lateralidad, resumen_clinico, estado, enviada_en)
VALUES
    (v_paciente, v_medico, v_esp, v_proc,
     'Tórax — esternotomía media', 'NO_APLICA',
     'Enfermedad coronaria de tres vasos. Indicación de revascularización.',
     'PROGRAMADA', localtimestamp)
    RETURNING id INTO v_solicitud;

-- Requerimientos: copia de los roles predeterminados del procedimiento
INSERT INTO requerimientos_roles_solicitud (solicitud_cirugia_id, rol_clinico_id, especialidad_id, cantidad, es_requerido)
SELECT v_solicitud, rol_clinico_id, especialidad_id, cantidad_predeterminada, es_requerido
FROM roles_predeterminados_procedimiento
WHERE procedimiento_id = v_proc;

-- Programación
INSERT INTO cirugias
(solicitud_cirugia_id, quirofano_id, programada_por_usuario_id, coordinador_usuario_id,
 inicio_programado, fin_programado)
VALUES
    (v_solicitud, v_quirofano, v_jefe, v_jefe, v_inicio, v_inicio + interval '4 hours')
    RETURNING id INTO v_cirugia;

-- Asignaciones (cada persona cubre el requerimiento de su rol)
INSERT INTO asignaciones_personal_cirugia
(cirugia_id, solicitud_cirugia_id, requerimiento_rol_id, profesional_id,
 estado, confirmado_en, es_operador_tablero, asignado_por_usuario_id)
SELECT v_cirugia, v_solicitud, req.id, pp.id,
       'CONFIRMADA', localtimestamp, x.operador, v_jefe
FROM (VALUES
          ('alejandro.martinez@demo.local', 'CIRUJANO',                  false),
          ('laura.rodriguez@demo.local',    'ANESTESIOLOGO',             false),
          ('maria.fernandez@demo.local',    'INSTRUMENTADOR_QUIRURGICO', false),
          ('carlos.perez@demo.local',       'AUXILIAR_CIRCULANTE',       true),   -- operador del tablero
          ('andres.gomez@demo.local',       'PERFUSIONISTA',             false)
     ) AS x (correo, rol, operador)
         JOIN usuarios u                         ON u.correo = x.correo
         JOIN perfiles_profesionales pp          ON pp.usuario_id = u.id
         JOIN roles_clinicos rc                  ON rc.codigo = x.rol
         JOIN requerimientos_roles_solicitud req ON req.solicitud_cirugia_id = v_solicitud
    AND req.rol_clinico_id = rc.id;

-- Datos preoperatorios (snapshot, incluida la copia de alergias)
INSERT INTO datos_preoperatorios_cirugia
(cirugia_id, peso_kg, talla_cm, glucometria_mg_dl,
 requiere_reserva_sangre, estado_reserva_sangre, copia_alergias)
SELECT v_cirugia, 78.5, 172, 104,
       true, 'RESERVADA',
       jsonb_agg(jsonb_build_object('sustancia', a.sustancia, 'reaccion', a.reaccion, 'severidad', a.severidad))
FROM alergias_paciente a
WHERE a.paciente_id = v_paciente AND a.activo;

-- Checklist copiado desde la plantilla predeterminada
PERFORM fn_iniciar_checklist(v_cirugia);

  -- Instrumental copiado desde el predeterminado del procedimiento
  PERFORM fn_preparar_instrumental(v_cirugia);

  -- María (instrumentadora) deja preparado SOLO el set cardiovascular
UPDATE sets_instrumentales_cirugia sc
SET cantidad_preparada = sc.cantidad_requerida,
    preparado_por_asignacion_id = a.id,
    preparado_en = localtimestamp
    FROM asignaciones_personal_cirugia a
  JOIN perfiles_profesionales pp ON pp.id = a.profesional_id
    JOIN usuarios u ON u.id = pp.usuario_id
WHERE sc.cirugia_id = v_cirugia
  AND sc.codigo_set = 'SET_CARDIOVASCULAR_BASICO'
  AND a.cirugia_id = v_cirugia
  AND u.correo = 'maria.fernandez@demo.local';
END;
$$;


-- ---------------------------------------------------------------------
-- ACCESO DEMO
-- Administrador y contraseña 'demo1234' para TODOS los usuarios demo.
-- crypt(..., gen_salt('bf')) genera hashes $2a$, compatibles con
-- BCryptPasswordEncoder de Spring Security.
-- ---------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO usuarios (nombres, apellidos, correo) VALUES
    ('Administrador', 'Demo', 'admin@demo.local');

INSERT INTO usuario_roles_sistema (usuario_id, rol_sistema_id)
SELECT u.id, r.id
FROM usuarios u
         JOIN roles_sistema r ON r.codigo = 'ADMIN'
WHERE u.correo = 'admin@demo.local';

UPDATE usuarios
SET hash_contrasena = crypt('demo1234', gen_salt('bf', 10))
WHERE correo LIKE '%@demo.local';
