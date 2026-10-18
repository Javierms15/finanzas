package com.tfg.finanzas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ImportacionDtos {

    /**
     * Movimiento tal y como se extrae de un fichero, antes de deduplicar y persistir.
     */
    public record MovimientoImportado(LocalDate fecha, BigDecimal importe, String descripcionOriginal) {
    }

    /**
     * `duplicadosProbables` son movimientos con la misma cuenta+fecha+importe que
     * uno ya existente pero con una descripcion distinta — no coinciden por hash
     * exacto (p. ej. el mismo banco describe la misma operacion de forma distinta
     * en dos formatos de extracto distintos), pero se tratan igualmente como
     * duplicados para no inflar el saldo, dejando constancia de que la deteccion
     * no fue por coincidencia exacta.
     */
    public record ResultadoImportacion(int totalEncontrados, int nuevos, int duplicados, int duplicadosProbables) {
    }
}
