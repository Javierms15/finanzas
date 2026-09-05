package com.tfg.finanzas.patrimonio;

import java.math.BigDecimal;

public record ProyeccionEfectivo(BigDecimal patrimonioActual, BigDecimal ingresoMedio, BigDecimal gastoMedio,
                                  int meses, BigDecimal patrimonioProyectado) {
}
