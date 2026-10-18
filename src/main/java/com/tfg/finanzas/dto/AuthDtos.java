package com.tfg.finanzas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    // 72 = limite real de BCrypt: a partir de 72 bytes ignora el resto de la contrasena en silencio
    public record RegisterRequest(
            @NotBlank(message = "Indica tu nombre") @Size(max = 80, message = "El nombre es demasiado largo (máximo 80 caracteres)") String nombre,
            @NotBlank(message = "Indica tu email") @Email(message = "El email no es válido") String email,
            @NotBlank(message = "Indica una contraseña")
            @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres") String password) {
    }

    // En el login no se exige longitud minima: solo debe coincidir con la guardada
    public record LoginRequest(
            @NotBlank(message = "Indica tu email") @Email(message = "El email no es válido") String email,
            @NotBlank(message = "Indica tu contraseña") String password) {
    }

    public record TokenResponse(String token, UsuarioDtos.Response usuario) {
    }
}
