package com.tfg.finanzas.cuenta;

import com.tfg.finanzas.usuario.Usuario;
import com.tfg.finanzas.usuario.UsuarioRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/usuarios/{usuarioId}/cuentas")
@RequiredArgsConstructor
public class CuentaBancariaController {

    private final CuentaBancariaRepository cuentaBancariaRepository;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public List<CuentaBancaria> listar(@PathVariable Long usuarioId) {
        return cuentaBancariaRepository.findByUsuarioId(usuarioId);
    }

    @PostMapping
    public ResponseEntity<CuentaBancaria> crear(@PathVariable Long usuarioId,
                                                 @RequestBody @Valid CrearCuentaRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + usuarioId));
        CuentaBancaria cuenta = CuentaBancaria.builder()
                .usuario(usuario)
                .banco(request.banco())
                .tipo(request.tipo())
                .alias(request.alias())
                .moneda(request.moneda())
                .saldoInicial(request.saldoInicial() != null ? request.saldoInicial() : BigDecimal.ZERO)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(cuentaBancariaRepository.save(cuenta));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> manejarUsuarioNoEncontrado(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    public record CrearCuentaRequest(@NotBlank String banco, @NotNull TipoCuenta tipo, @NotBlank String alias,
                                      @NotBlank String moneda, BigDecimal saldoInicial) {
    }
}
