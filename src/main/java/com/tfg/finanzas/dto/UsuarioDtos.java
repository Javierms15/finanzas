package com.tfg.finanzas.dto;

import com.tfg.finanzas.model.Usuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UsuarioDtos {

    public record CambiarPasswordRequest(
            @NotBlank(message = "Indica tu contraseña actual") String passwordActual,
            @NotBlank(message = "Indica la contraseña nueva")
            @Size(min = 8, max = 72, message = "La contraseña nueva debe tener entre 8 y 72 caracteres") String passwordNueva) {
    }

    public record Response(Long id, String nombre, String email) {

        public static Response from(Usuario usuario) {
            return new Response(usuario.getId(), usuario.getNombre(), usuario.getEmail());
        }
    }
}
