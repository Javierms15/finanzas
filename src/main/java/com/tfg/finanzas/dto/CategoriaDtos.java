package com.tfg.finanzas.dto;

import com.tfg.finanzas.model.TipoCategoria;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CategoriaDtos {

    public record CrearCategoriaRequest(@NotBlank String nombre, @NotNull TipoCategoria tipo, String color) {
    }

    public record ResultadoSugeridas(int categoriasCreadas, int reglasCreadas) {
    }
}
