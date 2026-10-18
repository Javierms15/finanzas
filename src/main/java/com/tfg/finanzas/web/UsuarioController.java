package com.tfg.finanzas.web;

import com.tfg.finanzas.dto.UsuarioDtos.Response;
import com.tfg.finanzas.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;

    /** Antes habia un GET /usuarios que devolvia nombre y email de TODOS los usuarios a cualquiera con sesion. */
    @GetMapping("/me")
    public Response yo(Authentication autenticacion) {
        return usuarioRepository.findByEmail(autenticacion.getName()).map(Response::from).orElseThrow();
    }
}
