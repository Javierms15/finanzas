package com.tfg.finanzas.service;

import com.tfg.finanzas.dto.PatrimonioDtos.RendimientoMensual;
import com.tfg.finanzas.model.Categoria;
import com.tfg.finanzas.model.Movimiento;
import com.tfg.finanzas.repository.MovimientoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Intereses, retenciones y cashback que el usuario ha recibido cada mes en cada banco, sumando los
 * movimientos de las categorias marcadas con un TipoRendimiento. No es una estimacion: son importes
 * reales de los extractos importados.
 */
@Service
@RequiredArgsConstructor
public class RendimientoMensualService {

    private final MovimientoRepository movimientoRepository;

    private static final class Acumulado {
        BigDecimal intereses = BigDecimal.ZERO;
        BigDecimal retenciones = BigDecimal.ZERO;
        BigDecimal cashback = BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public List<RendimientoMensual> porUsuario(Long usuarioId) {
        Map<YearMonth, Map<String, Acumulado>> porMes = new TreeMap<>();
        for (Movimiento m : movimientoRepository.findByCuentaUsuarioId(usuarioId)) {
            Categoria categoria = m.getCategoria();
            if (categoria == null || categoria.getRendimiento() == null) {
                continue;
            }
            Acumulado acumulado = porMes
                    .computeIfAbsent(YearMonth.from(m.getFecha()), mes -> new TreeMap<>())
                    .computeIfAbsent(m.getCuenta().getBanco(), banco -> new Acumulado());
            switch (categoria.getRendimiento()) {
                case INTERES -> acumulado.intereses = acumulado.intereses.add(m.getImporte());
                case RETENCION -> acumulado.retenciones = acumulado.retenciones.add(m.getImporte());
                case CASHBACK -> acumulado.cashback = acumulado.cashback.add(m.getImporte());
            }
        }
        List<RendimientoMensual> resultado = new ArrayList<>();
        porMes.forEach((mes, bancos) -> bancos.forEach((banco, a) ->
                resultado.add(new RendimientoMensual(mes, banco, a.intereses, a.retenciones, a.cashback))));
        return resultado;
    }
}
