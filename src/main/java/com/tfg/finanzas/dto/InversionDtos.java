package com.tfg.finanzas.dto;

import com.tfg.finanzas.model.TipoActivo;
import com.tfg.finanzas.model.TipoMovimientoInversion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public class InversionDtos {

    public record CrearActivoRequest(@NotBlank String ticker, @NotBlank String nombre, @NotNull TipoActivo tipo) {
    }

    public record RegistrarMovimientoRequest(@NotNull Long activoId, @NotNull TipoMovimientoInversion tipo,
                                              @NotNull LocalDate fecha, @NotNull BigDecimal cantidad,
                                              @NotNull BigDecimal precio) {
    }

    public record PosicionValorada(Long posicionId, String ticker, String nombreActivo, TipoActivo tipo,
                                    BigDecimal cantidad, BigDecimal precioMedioCompra, BigDecimal precioActual,
                                    BigDecimal valorActual, BigDecimal rendimiento) {
    }

    public record ProyeccionInversion(BigDecimal valorActual, BigDecimal retornoEsperado, int anios,
                                       BigDecimal aportacionAnual, BigDecimal valorProyectado) {
    }

    /**
     * Una posicion tal y como aparece en un extracto de cuenta de valores (foto
     * del momento: cantidad y precio actual, no historial de compras).
     */
    public record PosicionImportada(String isin, String nombre, TipoActivo tipo, BigDecimal cantidad, BigDecimal precio) {
    }

    public record ResultadoImportacionPosiciones(int totalEncontradas, int actualizadas) {
    }
}
