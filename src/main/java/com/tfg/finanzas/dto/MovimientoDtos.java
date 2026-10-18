package com.tfg.finanzas.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

public class MovimientoDtos {

    /** categoriaId null = quitar la categoria manual y volver a la automatica. */
    public record CambiarCategoriaRequest(Long categoriaId) {
    }

    public record AgregacionMensual(YearMonth mes, BigDecimal totalIngresos, BigDecimal totalGastos, BigDecimal balance) {
    }
}
