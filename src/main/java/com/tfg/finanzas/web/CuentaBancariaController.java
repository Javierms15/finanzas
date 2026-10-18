package com.tfg.finanzas.web;

import com.tfg.finanzas.dto.CuentaDtos.ActualizarSaldoInicialRequest;
import com.tfg.finanzas.dto.CuentaDtos.ActualizarTasaInteresRequest;
import com.tfg.finanzas.dto.CuentaDtos.CrearCuentaRequest;
import com.tfg.finanzas.dto.CuentaDtos.CuentaConSaldoActual;
import com.tfg.finanzas.model.CuentaBancaria;
import com.tfg.finanzas.model.Usuario;
import com.tfg.finanzas.repository.CuentaBancariaRepository;
import com.tfg.finanzas.repository.UsuarioRepository;
import com.tfg.finanzas.service.PatrimonioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/usuarios/{usuarioId}/cuentas")
@RequiredArgsConstructor
public class CuentaBancariaController {

    private final CuentaBancariaRepository cuentaBancariaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PatrimonioService patrimonioService;

    @GetMapping
    public List<CuentaBancaria> listar(@PathVariable Long usuarioId) {
        return cuentaBancariaRepository.findByUsuarioId(usuarioId);
    }

    @GetMapping("/saldos")
    public List<CuentaConSaldoActual> listarConSaldo(@PathVariable Long usuarioId) {
        Map<Long, BigDecimal> saldos = patrimonioService.saldosPorCuenta(usuarioId);
        return cuentaBancariaRepository.findByUsuarioId(usuarioId).stream()
                .map(c -> new CuentaConSaldoActual(c.getId(), c.getBanco(), c.getTipo(), c.getAlias(), c.getMoneda(),
                        c.getSaldoInicial(), c.getTasaInteresAnual(), saldos.get(c.getId())))
                .toList();
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
                .tasaInteresAnual(request.tasaInteresAnual())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(cuentaBancariaRepository.save(cuenta));
    }

    @PatchMapping("/{cuentaId}/saldo-inicial")
    public ResponseEntity<CuentaBancaria> actualizarSaldoInicial(@PathVariable Long usuarioId,
                                                                   @PathVariable Long cuentaId,
                                                                   @RequestBody @Valid ActualizarSaldoInicialRequest request) {
        CuentaBancaria cuenta = buscarCuentaDeUsuario(usuarioId, cuentaId);
        cuenta.setSaldoInicial(request.saldoInicial());
        return ResponseEntity.ok(cuentaBancariaRepository.save(cuenta));
    }

    @PatchMapping("/{cuentaId}/tasa-interes")
    public ResponseEntity<CuentaBancaria> actualizarTasaInteres(@PathVariable Long usuarioId,
                                                                  @PathVariable Long cuentaId,
                                                                  @RequestBody @Valid ActualizarTasaInteresRequest request) {
        CuentaBancaria cuenta = buscarCuentaDeUsuario(usuarioId, cuentaId);
        cuenta.setTasaInteresAnual(request.tasaInteresAnual());
        return ResponseEntity.ok(cuentaBancariaRepository.save(cuenta));
    }

    private CuentaBancaria buscarCuentaDeUsuario(Long usuarioId, Long cuentaId) {
        CuentaBancaria cuenta = cuentaBancariaRepository.findById(cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada: " + cuentaId));
        if (!cuenta.getUsuario().getId().equals(usuarioId)) {
            throw new IllegalArgumentException("La cuenta no pertenece al usuario indicado");
        }
        return cuenta;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> manejarUsuarioNoEncontrado(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
