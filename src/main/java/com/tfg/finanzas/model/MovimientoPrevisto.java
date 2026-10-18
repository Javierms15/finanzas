package com.tfg.finanzas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Ingreso (importe positivo) o pago (negativo) que el usuario espera pero que
 * todavia no aparece en ningun extracto, p. ej. dinero prestado a un amigo que
 * va a devolver. Mientras no este marcado como cumplido, la proyeccion lo cuenta
 * a partir de su fecha; al marcarlo (o borrarlo) deja de contar, para no
 * duplicarlo con el movimiento real cuando se importe el extracto.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimientoPrevisto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal importe;

    @Column(nullable = false)
    private String descripcion;

    @Builder.Default
    @Column(nullable = false)
    private boolean cumplido = false;
}
