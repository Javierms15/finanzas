package com.tfg.finanzas.cuenta;

import com.tfg.finanzas.usuario.Usuario;
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
}
