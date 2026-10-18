package com.tfg.finanzas.web;

import com.tfg.finanzas.dto.UsuarioDtos.CambiarPasswordRequest;
import com.tfg.finanzas.dto.UsuarioDtos.Response;
import com.tfg.finanzas.model.Usuario;
import com.tfg.finanzas.repository.UsuarioRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    /** Antes habia un GET /usuarios que devolvia nombre y email de TODOS los usuarios a cualquiera con sesion. */
    @GetMapping("/me")
    public Response yo(Authentication autenticacion) {
        return usuarioRepository.findByEmail(autenticacion.getName()).map(Response::from).orElseThrow();
    }

    /**
     * Los errores son 400 y no 401 a proposito: el frontend cierra la sesion ante cualquier 401, y
     * equivocarse al escribir la contrasena actual no debe echar al usuario. Las sesiones ya abiertas
     * siguen valiendo hasta que caduquen (JWT sin estado).
     */
    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarPassword(Authentication autenticacion, @RequestBody @Valid CambiarPasswordRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(autenticacion.getName()).orElseThrow();
        if (!passwordEncoder.matches(request.passwordActual(), usuario.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta");
        }
        if (request.passwordActual().equals(request.passwordNueva())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña nueva debe ser distinta de la actual");
        }
        usuario.setPasswordHash(passwordEncoder.encode(request.passwordNueva()));
        usuarioRepository.save(usuario);
    }
}
