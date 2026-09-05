package com.tfg.finanzas.patrimonio;

import com.tfg.finanzas.movimiento.AgregacionMensual;
import com.tfg.finanzas.movimiento.AgregacionMensualService;
import com.tfg.finanzas.movimiento.Movimiento;
import com.tfg.finanzas.movimiento.MovimientoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Proyeccion lineal de finanzas diarias (solo efectivo, la inversion tiene su
 * propia proyeccion compuesta): patrimonio_actual + (ingreso_medio - gasto_medio) x meses.
 * <p>
 * Los promedios se calculan sobre los meses ya cerrados: se excluye el mes en
 * curso porque esta incompleto y sesgaria la media a la baja. gastoMedio se
 * expresa siempre en positivo (importe medio gastado), no como el signo negativo
 * que llevan los movimientos internamente.
 */
@Service
@RequiredArgsConstructor
public class PatrimonioProyeccionService {

    private final PatrimonioService patrimonioService;
    private final AgregacionMensualService agregacionMensualService;
    private final MovimientoRepository movimientoRepository;

    public ProyeccionEfectivo proyectar(Long usuarioId, int meses) {
        BigDecimal patrimonioActual = patrimonioService.calcularSaldoEfectivoTotal(usuarioId);
        PromedioMensual promedio = calcularPromedioMensual(usuarioId);
        BigDecimal proyectado = proyectarValor(patrimonioActual, promedio.ingresoMedio(), promedio.gastoMedio(), meses);
        return new ProyeccionEfectivo(patrimonioActual, promedio.ingresoMedio(), promedio.gastoMedio(), meses, proyectado);
    }

    /**
     * "Que pasaria si ajusto esta categoria": se resta el ajuste del gasto medio
     * de esa categoria (y por tanto del gasto medio total, que es la suma de
     * todas las categorias) y se recalcula la proyeccion, todo en memoria sin
     * tocar ningun movimiento real.
     */
    public SimulacionAjusteCategoria simular(Long usuarioId, int meses, Long categoriaId, BigDecimal ajusteMensual) {
        BigDecimal patrimonioActual = patrimonioService.calcularSaldoEfectivoTotal(usuarioId);
        PromedioMensual promedio = calcularPromedioMensual(usuarioId);
        BigDecimal gastoMedioCategoria = calcularGastoMedioCategoria(usuarioId, categoriaId);

        BigDecimal gastoMedioCategoriaSimulado = gastoMedioCategoria.subtract(ajusteMensual);
        BigDecimal gastoMedioTotalSimulado = promedio.gastoMedio().subtract(ajusteMensual);

        BigDecimal proyectadoActual = proyectarValor(patrimonioActual, promedio.ingresoMedio(), promedio.gastoMedio(), meses);
        BigDecimal proyectadoSimulado = proyectarValor(patrimonioActual, promedio.ingresoMedio(), gastoMedioTotalSimulado, meses);

        return new SimulacionAjusteCategoria(categoriaId, meses, ajusteMensual,
                gastoMedioCategoria, gastoMedioCategoriaSimulado,
                promedio.gastoMedio(), gastoMedioTotalSimulado,
                patrimonioActual, proyectadoActual, proyectadoSimulado);
    }

    private BigDecimal proyectarValor(BigDecimal patrimonioActual, BigDecimal ingresoMedio, BigDecimal gastoMedio, int meses) {
        return patrimonioActual.add(ingresoMedio.subtract(gastoMedio).multiply(BigDecimal.valueOf(meses)));
    }

    private PromedioMensual calcularPromedioMensual(Long usuarioId) {
        List<AgregacionMensual> mesesCerrados = mesesCerrados(agregacionMensualService.porUsuario(usuarioId));
        if (mesesCerrados.isEmpty()) {
            return new PromedioMensual(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal totalIngresos = sumar(mesesCerrados, AgregacionMensual::totalIngresos);
        BigDecimal totalGastos = sumar(mesesCerrados, AgregacionMensual::totalGastos);
        BigDecimal numMeses = BigDecimal.valueOf(mesesCerrados.size());
        BigDecimal ingresoMedio = totalIngresos.divide(numMeses, 2, RoundingMode.HALF_UP);
        BigDecimal gastoMedio = totalGastos.abs().divide(numMeses, 2, RoundingMode.HALF_UP);
        return new PromedioMensual(ingresoMedio, gastoMedio);
    }

    private BigDecimal calcularGastoMedioCategoria(Long usuarioId, Long categoriaId) {
        List<AgregacionMensual> mesesCerrados = mesesCerrados(agregacionMensualService.porUsuario(usuarioId));
        if (mesesCerrados.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Set<YearMonth> mesesValidos = mesesCerrados.stream().map(AgregacionMensual::mes).collect(Collectors.toSet());
        BigDecimal totalGastoCategoria = movimientoRepository.findByCuentaUsuarioId(usuarioId).stream()
                .filter(m -> m.getCategoria() != null && m.getCategoria().getId().equals(categoriaId))
                .filter(m -> mesesValidos.contains(YearMonth.from(m.getFecha())))
                .map(Movimiento::getImporte)
                .filter(importe -> importe.signum() < 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalGastoCategoria.abs().divide(BigDecimal.valueOf(mesesCerrados.size()), 2, RoundingMode.HALF_UP);
    }

    private List<AgregacionMensual> mesesCerrados(List<AgregacionMensual> todos) {
        YearMonth mesActual = YearMonth.now();
        return todos.stream().filter(a -> !a.mes().equals(mesActual)).toList();
    }

    private BigDecimal sumar(List<AgregacionMensual> agregados, java.util.function.Function<AgregacionMensual, BigDecimal> extractor) {
        return agregados.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private record PromedioMensual(BigDecimal ingresoMedio, BigDecimal gastoMedio) {
    }
}
