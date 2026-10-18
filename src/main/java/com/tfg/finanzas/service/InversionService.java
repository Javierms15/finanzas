package com.tfg.finanzas.service;

import com.tfg.finanzas.dto.InversionDtos.PosicionValorada;
import com.tfg.finanzas.model.Posicion;
import com.tfg.finanzas.repository.PosicionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Si el proveedor de precios no devuelve cotizacion (ticker no soportado por
 * Yahoo Finance, fallo de red, etc.) se usa el precio medio de compra como
 * respaldo: es mejor una valoracion aproximada por coste que no poder valorar
 * la posicion en absoluto.
 */
@Service
@RequiredArgsConstructor
public class InversionService {

    private final PosicionRepository posicionRepository;
    private final PrecioMercadoProvider precioMercadoProvider;

    public List<PosicionValorada> valorarCartera(Long usuarioId) {
        return posicionRepository.findByUsuarioId(usuarioId).stream()
                .map(this::valorar)
                .toList();
    }

    public BigDecimal valorTotalCartera(Long usuarioId) {
        return valorarCartera(usuarioId).stream()
                .map(PosicionValorada::valorActual)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PosicionValorada valorar(Posicion posicion) {
        BigDecimal costeBase = posicion.getCantidad().multiply(posicion.getPrecioMedioCompra());
        BigDecimal precioActual = precioMercadoProvider.obtenerPrecioActual(posicion.getActivo().getTicker())
                .orElse(posicion.getPrecioMedioCompra());
        BigDecimal valorActual = posicion.getCantidad().multiply(precioActual);
        BigDecimal rendimiento = valorActual.subtract(costeBase);
        return new PosicionValorada(posicion.getId(), posicion.getActivo().getTicker(), posicion.getActivo().getNombre(),
                posicion.getActivo().getTipo(), posicion.getCantidad(), posicion.getPrecioMedioCompra(), precioActual,
                valorActual, rendimiento);
    }
}
