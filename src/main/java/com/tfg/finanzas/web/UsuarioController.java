package com.tfg.finanzas.web;

import com.tfg.finanzas.dto.UsuarioDtos.Response;
import com.tfg.finanzas.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public List<Response> listar() {
        return usuarioRepository.findAll().stream().map(Response::from).toList();
    }
}
