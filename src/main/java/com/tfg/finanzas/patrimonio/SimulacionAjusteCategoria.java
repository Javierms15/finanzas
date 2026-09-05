package com.tfg.finanzas.patrimonio;

import java.math.BigDecimal;

public record SimulacionAjusteCategoria(Long categoriaId, int meses, BigDecimal ajusteMensual,
                                         BigDecimal gastoMedioCategoriaActual, BigDecimal gastoMedioCategoriaSimulado,
                                         BigDecimal gastoMedioTotalActual, BigDecimal gastoMedioTotalSimulado,
                                         BigDecimal patrimonioActual, BigDecimal patrimonioProyectadoActual,
                                         BigDecimal patrimonioProyectadoSimulado) {
}
