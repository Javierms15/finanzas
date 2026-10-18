package com.tfg.finanzas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuentaBancaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private String banco;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCuenta tipo;

    @Column(nullable = false)
    private String alias;

    @Column(nullable = false)
    private String moneda;

    /**
     * Saldo de la cuenta antes del primer movimiento importado. No tenemos forma
     * de conocer el saldo real solo a partir de los movimientos importados (un
     * extracto no siempre cubre toda la vida de la cuenta), asi que el saldo
     * actual se calcula como saldoInicial + suma de movimientos.
     */
    @Builder.Default
    @Column(nullable = false)
    private BigDecimal saldoInicial = BigDecimal.ZERO;

    /**
     * TAE anual de una cuenta remunerada (p. ej. 0.01 = 1%), null si no es
     * remunerada o no se conoce. Se usa en el frontend (Resumen) para proyectar
     * el interes esperado de esta cuenta por separado del ahorro por ingresos.
     * Precision/scale explicitos: sin ellos Hibernate usa scale=2 por defecto
     * (el mismo que vale para dinero), lo que redondeaba en silencio un interes
     * tipo 0.0089 (0,89%) a 0.01 (1%) al guardar.
     */
    @Column(precision = 9, scale = 6)
    private BigDecimal tasaInteresAnual;
}
