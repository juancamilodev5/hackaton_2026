-- Iteración 2 (CRUD): permisos para entidades que la semilla no cubría.
-- Idempotente y sin dependencias de los datos demo, así que puede aplicarse
-- después de V100 en bases de desarrollo existentes (spring.flyway.out-of-order en dev).

INSERT INTO permisos (codigo, nombre) VALUES
    ('PACIENTES_VER',            'Ver pacientes y sus alergias'),
    ('PACIENTES_GESTIONAR',      'Registrar y editar pacientes y sus alergias'),
    ('CITAS_VER',                'Ver citas'),
    ('CITAS_GESTIONAR',          'Agendar y actualizar citas'),
    ('PROFESIONALES_VER',        'Ver profesionales, sus roles clínicos, especialidades y disponibilidad'),
    ('PROFESIONALES_GESTIONAR',  'Gestionar perfiles profesionales, roles clínicos y especialidades'),
    ('DISPONIBILIDAD_GESTIONAR', 'Registrar disponibilidad de profesionales'),
    ('INCIDENTES_GESTIONAR',     'Reportar y gestionar incidentes de una cirugía')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO rol_sistema_permisos (rol_sistema_id, permiso_id)
SELECT r.id, p.id
FROM (VALUES
          -- ADMIN: todo
          ('ADMIN', 'PACIENTES_VER'), ('ADMIN', 'PACIENTES_GESTIONAR'),
          ('ADMIN', 'CITAS_VER'), ('ADMIN', 'CITAS_GESTIONAR'),
          ('ADMIN', 'PROFESIONALES_VER'), ('ADMIN', 'PROFESIONALES_GESTIONAR'),
          ('ADMIN', 'DISPONIBILIDAD_GESTIONAR'), ('ADMIN', 'INCIDENTES_GESTIONAR'),
          -- MEDICO: atiende pacientes, agenda citas, solicita cirugías
          ('MEDICO', 'PACIENTES_VER'), ('MEDICO', 'PACIENTES_GESTIONAR'),
          ('MEDICO', 'CITAS_VER'), ('MEDICO', 'CITAS_GESTIONAR'),
          ('MEDICO', 'PROFESIONALES_VER'), ('MEDICO', 'INCIDENTES_GESTIONAR'),
          -- ENFERMERA_JEFE: necesita ver profesionales y su disponibilidad para asignar
          ('ENFERMERA_JEFE', 'PACIENTES_VER'), ('ENFERMERA_JEFE', 'PACIENTES_GESTIONAR'),
          ('ENFERMERA_JEFE', 'CITAS_VER'), ('ENFERMERA_JEFE', 'CITAS_GESTIONAR'),
          ('ENFERMERA_JEFE', 'PROFESIONALES_VER'), ('ENFERMERA_JEFE', 'DISPONIBILIDAD_GESTIONAR'),
          ('ENFERMERA_JEFE', 'INCIDENTES_GESTIONAR'),
          -- PERSONAL_QUIRURGICO: reporta novedades durante la cirugía
          ('PERSONAL_QUIRURGICO', 'INCIDENTES_GESTIONAR'),
          -- AUDITOR_CALIDAD: solo lectura
          ('AUDITOR_CALIDAD', 'PACIENTES_VER'), ('AUDITOR_CALIDAD', 'CITAS_VER'),
          ('AUDITOR_CALIDAD', 'PROFESIONALES_VER')
     ) AS x (rol, permiso)
         JOIN roles_sistema r ON r.codigo = x.rol
         JOIN permisos      p ON p.codigo = x.permiso
ON CONFLICT DO NOTHING;
