package com.hackaton.ulibre.eventos;

/**
 * Códigos de {@code eventos_cirugia.tipo_evento}. El esquema V4 deja la columna como varchar libre
 * (no hay catálogo ni enum en SQL): este es el único lugar donde se definen.
 */
public enum TipoEvento {
    // Programación y equipo
    CIRUGIA_PROGRAMADA,
    CIRUGIA_REPROGRAMADA,
    ESTADO_CAMBIADO,
    PERSONAL_ASIGNADO,
    ASIGNACION_ESTADO_CAMBIADO,
    OPERADOR_DESIGNADO,
    OPERADOR_RETIRADO,
    PREOPERATORIO_REGISTRADO,
    PREOPERATORIO_VALIDADO,
    // Checklist
    CHECKLIST_INICIADO,
    ITEM_REGISTRADO,
    ITEM_CONFIRMADO,
    FASE_CERRADA,
    CHECKLIST_COMPLETADO,
    // Instrumental y recuentos
    INSTRUMENTAL_COPIADO,
    INSTRUMENTAL_PREPARADO,
    RECUENTO_INICIAL_REGISTRADO,
    RECUENTO_MATERIAL_AGREGADO,
    RECUENTO_FINAL_REGISTRADO,
    RECUENTO_CONFIRMADO,
    // Hitos
    HITO_REGISTRADO,
    HITO_ANULADO,
    // Alertas e incidentes
    ALERTA_GENERADA,
    ALERTA_RECONOCIDA,
    ALERTA_RESUELTA,
    ALERTA_DESCARTADA,
    EXCEPCION_AUTORIZADA,
    INCIDENTE_REPORTADO,
    INCIDENTE_ACTUALIZADO
}
