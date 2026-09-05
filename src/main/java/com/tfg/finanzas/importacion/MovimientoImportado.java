package com.tfg.finanzas.importacion;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Movimiento tal y como se extrae de un fichero, antes de deduplicar y persistir.
 */
public record MovimientoImportado(LocalDate fecha, BigDecimal importe, String descripcionOriginal) {
}
