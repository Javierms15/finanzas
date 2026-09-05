package com.tfg.finanzas.inversion;

import java.math.BigDecimal;

public record PosicionValorada(Long posicionId, String ticker, String nombreActivo, BigDecimal cantidad,
                                BigDecimal precioMedioCompra, BigDecimal precioActual, BigDecimal valorActual,
                                BigDecimal rendimiento) {
}
