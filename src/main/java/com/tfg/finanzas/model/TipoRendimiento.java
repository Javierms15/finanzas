package com.tfg.finanzas.model;

/**
 * Marca las categorias cuyos movimientos son un rendimiento del dinero parado, para mostrarlos aparte
 * (pantalla Resumen). Es independiente del tipo (GASTO/INGRESO): una retencion es un gasto.
 */
public enum TipoRendimiento {
    /** Interes abonado por un banco. */
    INTERES,
    /** Retencion fiscal sobre ese interes (importe negativo): interes neto = INTERES + RETENCION. */
    RETENCION,
    /** Devolucion por usar la tarjeta o el broker (cashback, saveback). */
    CASHBACK
}
