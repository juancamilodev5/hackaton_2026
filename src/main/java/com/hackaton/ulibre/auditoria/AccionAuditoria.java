package com.hackaton.ulibre.auditoria;

/** Códigos de {@code registros_auditoria.accion} (varchar libre en el esquema). */
public enum AccionAuditoria {
    CREAR,
    ACTUALIZAR,
    DESACTIVAR,
    ELIMINAR,
    CAMBIAR_ESTADO,
    /** Reemplazo de un conjunto (roles, permisos, especialidades...). */
    REEMPLAZAR,
    ASOCIAR,
    DESASOCIAR,
    CAMBIAR_CONTRASENA,
    PUBLICAR,
    RETIRAR,
    NUEVA_VERSION
}
