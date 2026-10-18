package com.tfg.finanzas.service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Fuente externa de precios de mercado. Devuelve Optional en vez de lanzar
 * porque una cotizacion puede no estar disponible (ticker desconocido, proveedor
 * caido, etc.) sin que eso deba tumbar el calculo de valor de toda la cartera.
 */
public interface PrecioMercadoProvider {

    Optional<BigDecimal> obtenerPrecioActual(String ticker);
}
