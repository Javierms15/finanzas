package com.tfg.finanzas.usuario;

public record UsuarioResponse(Long id, String nombre, String email) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getEmail());
    }
}
