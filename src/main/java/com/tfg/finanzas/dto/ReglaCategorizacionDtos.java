package com.tfg.finanzas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ReglaCategorizacionDtos {

    public record CrearReglaRequest(@NotNull Long categoriaId, @NotBlank String patron, @NotNull Integer prioridad) {
    }
}
