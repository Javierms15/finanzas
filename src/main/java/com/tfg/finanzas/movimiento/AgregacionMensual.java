package com.tfg.finanzas.movimiento;

import java.math.BigDecimal;
import java.time.YearMonth;

public record AgregacionMensual(YearMonth mes, BigDecimal totalIngresos, BigDecimal totalGastos, BigDecimal balance) {
}
