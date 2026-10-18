package com.tfg.finanzas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthDtos {

    public record RegisterRequest(@NotBlank String nombre, @Email @NotBlank String email, @NotBlank String password) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record TokenResponse(String token, UsuarioDtos.Response usuario) {
    }
}
