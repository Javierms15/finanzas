package com.tfg.finanzas.dto;

import com.tfg.finanzas.model.TipoCuenta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CuentaDtos {

    public record CrearCuentaRequest(@NotBlank String banco, @NotNull TipoCuenta tipo, @NotBlank String alias,
                                      @NotBlank String moneda, BigDecimal saldoInicial, BigDecimal tasaInteresAnual) {
    }

    public record ActualizarSaldoInicialRequest(@NotNull BigDecimal saldoInicial) {
    }

    public record ActualizarTasaInteresRequest(BigDecimal tasaInteresAnual) {
    }

    public record CuentaConSaldoActual(Long id, String banco, TipoCuenta tipo, String alias, String moneda,
                                        BigDecimal saldoInicial, BigDecimal tasaInteresAnual, BigDecimal saldoActual,
                                        LocalDate ultimoMovimiento) {
    }
}
