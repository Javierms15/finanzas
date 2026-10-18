package com.tfg.finanzas.dto;

import com.tfg.finanzas.model.Usuario;

public class UsuarioDtos {

    public record Response(Long id, String nombre, String email) {

        public static Response from(Usuario usuario) {
            return new Response(usuario.getId(), usuario.getNombre(), usuario.getEmail());
        }
    }
}
