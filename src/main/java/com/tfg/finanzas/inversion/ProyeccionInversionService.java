package com.tfg.finanzas.inversion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Proyeccion compuesta: valor_actual x (1 + retorno_esperado)^anios + aportaciones
 * periodicas. Las aportaciones se suman en total (aportacionAnual x anios) sin
 * componer su propio retorno: es la misma simplificacion "todo en memoria" que
 * la proyeccion lineal de finanzas diarias, no un calculo de anualidad completo.
 */
@Service
@RequiredArgsConstructor
public class ProyeccionInversionService {

    private final InversionService inversionService;

    public ProyeccionInversion proyectar(Long usuarioId, int anios, BigDecimal retornoEsperado, BigDecimal aportacionAnual) {
        BigDecimal valorActual = inversionService.valorTotalCartera(usuarioId);
        BigDecimal factorCrecimiento = BigDecimal.ONE.add(retornoEsperado).pow(anios);
        BigDecimal valorCompuesto = valorActual.multiply(factorCrecimiento);
        BigDecimal totalAportaciones = aportacionAnual.multiply(BigDecimal.valueOf(anios));
        BigDecimal valorProyectado = valorCompuesto.add(totalAportaciones).setScale(2, RoundingMode.HALF_UP);
        return new ProyeccionInversion(valorActual.setScale(2, RoundingMode.HALF_UP), retornoEsperado, anios,
                aportacionAnual, valorProyectado);
    }
}
