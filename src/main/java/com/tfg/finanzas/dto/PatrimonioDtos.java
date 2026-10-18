package com.tfg.finanzas.dto;

import java.math.BigDecimal;

public class PatrimonioDtos {

    public record ProyeccionEfectivo(BigDecimal patrimonioActual, BigDecimal ingresoMedio, BigDecimal gastoMedio,
                                      int meses, BigDecimal patrimonioProyectado) {
    }

    public record SimulacionAjusteCategoria(Long categoriaId, int meses, BigDecimal ajusteMensual,
                                             BigDecimal gastoMedioCategoriaActual, BigDecimal gastoMedioCategoriaSimulado,
                                             BigDecimal gastoMedioTotalActual, BigDecimal gastoMedioTotalSimulado,
                                             BigDecimal patrimonioActual, BigDecimal patrimonioProyectadoActual,
                                             BigDecimal patrimonioProyectadoSimulado) {
    }
}
