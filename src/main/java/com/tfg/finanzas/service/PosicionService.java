package com.tfg.finanzas.service;

import com.tfg.finanzas.dto.InversionDtos.PosicionImportada;
import com.tfg.finanzas.model.Activo;
import com.tfg.finanzas.model.MovimientoInversion;
import com.tfg.finanzas.model.Posicion;
import com.tfg.finanzas.model.TipoMovimientoInversion;
import com.tfg.finanzas.model.Usuario;
import com.tfg.finanzas.repository.ActivoRepository;
import com.tfg.finanzas.repository.MovimientoInversionRepository;
import com.tfg.finanzas.repository.PosicionRepository;
import com.tfg.finanzas.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Mantiene Posicion.cantidad y Posicion.precioMedioCompra al hilo de cada
 * compra/venta/dividendo. El precio medio se recalcula como coste medio
 * ponderado (media ponderada del precio de compra existente y el nuevo); en
 * una venta no cambia, solo baja la cantidad.
 */
@Service
@RequiredArgsConstructor
public class PosicionService {

    private final PosicionRepository posicionRepository;
    private final MovimientoInversionRepository movimientoInversionRepository;
    private final ActivoRepository activoRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public Posicion registrarMovimiento(Long usuarioId, Long activoId, TipoMovimientoInversion tipo,
                                         LocalDate fecha, BigDecimal cantidad, BigDecimal precio) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + usuarioId));
        Activo activo = activoRepository.findById(activoId)
                .orElseThrow(() -> new IllegalArgumentException("Activo no encontrado: " + activoId));

        Posicion posicion = posicionRepository.findByUsuarioIdAndActivoId(usuarioId, activoId)
                .orElseGet(() -> Posicion.builder()
                        .usuario(usuario)
                        .activo(activo)
                        .cantidad(BigDecimal.ZERO)
                        .precioMedioCompra(BigDecimal.ZERO)
                        .build());

        switch (tipo) {
            case COMPRA -> aplicarCompra(posicion, cantidad, precio);
            case VENTA -> aplicarVenta(posicion, cantidad);
            case DIVIDENDO -> {
                // Un dividendo no cambia ni la cantidad de titulos ni el precio medio.
            }
        }
        Posicion guardada = posicionRepository.save(posicion);

        MovimientoInversion movimiento = MovimientoInversion.builder()
                .posicion(guardada)
                .fecha(fecha)
                .tipo(tipo)
                .cantidad(cantidad)
                .precio(precio)
                .build();
        movimientoInversionRepository.save(movimiento);

        return guardada;
    }

    private void aplicarCompra(Posicion posicion, BigDecimal cantidad, BigDecimal precio) {
        BigDecimal costeExistente = posicion.getCantidad().multiply(posicion.getPrecioMedioCompra());
        BigDecimal costeNuevo = cantidad.multiply(precio);
        BigDecimal cantidadTotal = posicion.getCantidad().add(cantidad);

        BigDecimal precioMedio = cantidadTotal.signum() == 0
                ? BigDecimal.ZERO
                : costeExistente.add(costeNuevo).divide(cantidadTotal, 4, RoundingMode.HALF_UP);

        posicion.setCantidad(cantidadTotal);
        posicion.setPrecioMedioCompra(precioMedio);
    }

    private void aplicarVenta(Posicion posicion, BigDecimal cantidad) {
        if (posicion.getCantidad().compareTo(cantidad) < 0) {
            throw new IllegalArgumentException(
                    "No se pueden vender " + cantidad + " unidades, la posicion solo tiene " + posicion.getCantidad());
        }
        posicion.setCantidad(posicion.getCantidad().subtract(cantidad));
        if (posicion.getCantidad().signum() == 0) {
            posicion.setPrecioMedioCompra(BigDecimal.ZERO);
        }
    }

    /**
     * Aplica una posicion tal y como viene de un extracto de cuenta de valores:
     * a diferencia de {@link #registrarMovimiento}, esto no es una compra que se
     * suma a lo que ya habia, es una foto del momento, asi que la cantidad y el
     * precio se fijan directamente (no se recalcula una media ponderada ni se
     * genera un MovimientoInversion, porque no representa una operacion real).
     * El activo se identifica por ISIN (usado como ticker): si ese ISIN no se
     * puede resolver contra un proveedor de precios de mercado real, la cartera
     * sigue mostrando como valor el precio de este extracto en el momento de
     * importarlo, en vez de quedarse sin valorar.
     */
    @Transactional
    public void actualizarDesdeSnapshot(Long usuarioId, PosicionImportada datos) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + usuarioId));
        Activo activo = activoRepository.findByTicker(datos.isin())
                .orElseGet(() -> activoRepository.save(Activo.builder()
                        .ticker(datos.isin())
                        .nombre(datos.nombre())
                        .tipo(datos.tipo())
                        .build()));

        Posicion posicion = posicionRepository.findByUsuarioIdAndActivoId(usuarioId, activo.getId())
                .orElseGet(() -> Posicion.builder().usuario(usuario).activo(activo).build());
        posicion.setCantidad(datos.cantidad());
        posicion.setPrecioMedioCompra(datos.precio());
        posicionRepository.save(posicion);
    }
}
