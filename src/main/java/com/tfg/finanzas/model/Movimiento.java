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
import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private CuentaBancaria cuenta;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal importe;

    // Sin longitud fija: algunos bancos meten referencias muy largas
    // (transferencias de Hacienda, conceptos de Bizum con texto libre, etc.)
    // que superan el varchar(255) por defecto de Hibernate.
    @Column(nullable = false, columnDefinition = "text")
    private String descripcionOriginal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    private String etiquetaManual;

    /**
     * true si el usuario eligio la categoria a mano: la recategorizacion
     * automatica no la sobrescribe.
     */
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean categoriaManual = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigenMovimiento origen;

    @Column(nullable = false, unique = true)
    private String hashDedup;
}
