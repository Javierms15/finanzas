package com.tfg.finanzas.movimiento;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgregacionMensualService {

    private final MovimientoRepository movimientoRepository;

    public List<AgregacionMensual> porCuenta(Long cuentaId) {
        return agregar(movimientoRepository.findByCuentaId(cuentaId));
    }

    public List<AgregacionMensual> porUsuario(Long usuarioId) {
        return agregar(movimientoRepository.findByCuentaUsuarioId(usuarioId));
    }

    private List<AgregacionMensual> agregar(List<Movimiento> movimientos) {
        Map<YearMonth, List<Movimiento>> porMes = movimientos.stream()
                .collect(Collectors.groupingBy(m -> YearMonth.from(m.getFecha())));
        return porMes.entrySet().stream()
                .map(e -> construir(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(AgregacionMensual::mes))
                .toList();
    }

    private AgregacionMensual construir(YearMonth mes, List<Movimiento> movimientos) {
        BigDecimal ingresos = sumarSi(movimientos, importe -> importe.signum() > 0);
        BigDecimal gastos = sumarSi(movimientos, importe -> importe.signum() < 0);
        return new AgregacionMensual(mes, ingresos, gastos, ingresos.add(gastos));
    }

    private BigDecimal sumarSi(List<Movimiento> movimientos, Predicate<BigDecimal> filtro) {
        return movimientos.stream()
                .map(Movimiento::getImporte)
                .filter(filtro)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
