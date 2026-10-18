package com.tfg.finanzas.web;

import com.tfg.finanzas.dto.AuthDtos.LoginRequest;
import com.tfg.finanzas.dto.AuthDtos.RegisterRequest;
import com.tfg.finanzas.dto.AuthDtos.TokenResponse;
import com.tfg.finanzas.dto.UsuarioDtos.Response;
import com.tfg.finanzas.model.Usuario;
import com.tfg.finanzas.repository.UsuarioRepository;
import com.tfg.finanzas.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        String email = normalizarEmail(request.email());
        if (usuarioRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese email");
        }
        Usuario usuario = Usuario.builder()
                .nombre(request.nombre().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();
        Usuario guardado = usuarioRepository.save(usuario);
        return new TokenResponse(jwtService.generateToken(guardado.getEmail()), Response.from(guardado));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        String email = normalizarEmail(request.email());
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email o contraseña incorrectos");
        }
        // authenticationManager.authenticate ya valido que el usuario existe con estas credenciales
        Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow();
        return new TokenResponse(jwtService.generateToken(usuario.getEmail()), Response.from(usuario));
    }

    private static String normalizarEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
