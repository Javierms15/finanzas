package com.tfg.finanzas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ImportacionDtos {

    /**
     * Movimiento tal y como se extrae de un fichero, antes de deduplicar y persistir.
     * <p>
     * {@code subcuenta} solo viene informada cuando un mismo fichero reune varias cuentas (el "Extracto Integrado"
     * de ING trae "NOMINA" y "NARANJA"): el servicio de importacion usa ese nombre para llevar cada movimiento
     * a su cuenta. En el resto de formatos es null y todo va a la cuenta elegida por el usuario.
     */
    public record MovimientoImportado(LocalDate fecha, BigDecimal importe, String descripcionOriginal, String subcuenta) {

        public MovimientoImportado(LocalDate fecha, BigDecimal importe, String descripcionOriginal) {
            this(fecha, importe, descripcionOriginal, null);
        }
    }

    /**
     * `duplicadosProbables` son movimientos con la misma cuenta+fecha+importe que
     * uno ya existente pero con una descripcion distinta — no coinciden por hash
     * exacto (p. ej. el mismo banco describe la misma operacion de forma distinta
     * en dos formatos de extracto distintos), pero se tratan igualmente como
     * duplicados para no inflar el saldo, dejando constancia de que la deteccion
     * no fue por coincidencia exacta.
     * <p>
     * `cuentasCreadas` son los alias de las cuentas que la importacion ha tenido que crear porque el
     * fichero traia una subcuenta (p. ej. "Naranja") que el usuario aun no tenia.
     */
    public record ResultadoImportacion(int totalEncontrados, int nuevos, int duplicados, int duplicadosProbables,
                                        List<String> cuentasCreadas) {
    }
}
