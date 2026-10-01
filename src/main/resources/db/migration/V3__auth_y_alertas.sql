-- Contraseña de acceso (BCrypt). NULL: el usuario no puede iniciar sesión.
ALTER TABLE usuarios ADD COLUMN hash_contrasena varchar(100);

-- Evita alertas duplicadas de la misma regla mientras haya una vigente
CREATE UNIQUE INDEX ux_alerta_vigente_por_regla
    ON alertas (cirugia_id, regla_seguridad_id)
    WHERE regla_seguridad_id IS NOT NULL AND estado IN ('ABIERTA', 'RECONOCIDA');
