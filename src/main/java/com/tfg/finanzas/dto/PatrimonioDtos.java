package com.tfg.finanzas.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

public class PatrimonioDtos {

    /** Un mes y un banco: intereses abonados, retenciones (importe negativo) y cashback recibidos. */
    public record RendimientoMensual(YearMonth mes, String banco, BigDecimal intereses, BigDecimal retenciones,
                                      BigDecimal cashback) {
    }

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
