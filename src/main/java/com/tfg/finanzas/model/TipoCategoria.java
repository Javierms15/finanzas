package com.tfg.finanzas.model;

public enum TipoCategoria {
    GASTO,
    INGRESO,
    /** Mueve dinero sin ser ni ingreso ni gasto real (transferencias entre cuentas propias, aportaciones a inversion): no cuenta en ingresos/gastos. */
    NEUTRO
}
