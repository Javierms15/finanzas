package com.tfg.finanzas.dto;

import com.tfg.finanzas.model.MovimientoPrevisto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PrevistoDtos {

    public record CrearPrevistoRequest(@NotNull LocalDate fecha, @NotNull BigDecimal importe,
                                        @NotBlank String descripcion) {
    }

    public record ActualizarCumplidoRequest(@NotNull Boolean cumplido) {
    }

    public record Response(Long id, LocalDate fecha, BigDecimal importe, String descripcion, boolean cumplido) {

        public static Response from(MovimientoPrevisto p) {
            return new Response(p.getId(), p.getFecha(), p.getImporte(), p.getDescripcion(), p.isCumplido());
        }
    }
}
