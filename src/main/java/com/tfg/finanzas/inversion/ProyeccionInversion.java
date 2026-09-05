package com.tfg.finanzas.inversion;

import java.math.BigDecimal;

public record ProyeccionInversion(BigDecimal valorActual, BigDecimal retornoEsperado, int anios,
                                   BigDecimal aportacionAnual, BigDecimal valorProyectado) {
}
