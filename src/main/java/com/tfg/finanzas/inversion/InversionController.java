package com.tfg.finanzas.inversion;

import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/usuarios/{usuarioId}/inversion")
@RequiredArgsConstructor
public class InversionController {

    private final InversionService inversionService;
    private final PosicionService posicionService;
    private final ProyeccionInversionService proyeccionInversionService;

    @GetMapping("/posiciones")
    public List<PosicionValorada> posiciones(@PathVariable Long usuarioId) {
        return inversionService.valorarCartera(usuarioId);
    }

    @PostMapping("/movimientos")
    public ResponseEntity<Posicion> registrarMovimiento(@PathVariable Long usuarioId,
                                                          @RequestBody @Valid RegistrarMovimientoRequest request) {
        Posicion posicion = posicionService.registrarMovimiento(usuarioId, request.activoId(), request.tipo(),
                request.fecha(), request.cantidad(), request.precio());
        return ResponseEntity.status(HttpStatus.CREATED).body(posicion);
    }

    @GetMapping("/proyeccion")
    public ProyeccionInversion proyeccion(@PathVariable Long usuarioId,
                                           @RequestParam(defaultValue = "3") int anios,
                                           @RequestParam BigDecimal retornoEsperado,
                                           @RequestParam(defaultValue = "0") BigDecimal aportacionAnual) {
        return proyeccionInversionService.proyectar(usuarioId, anios, retornoEsperado, aportacionAnual);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> manejarError(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    public record RegistrarMovimientoRequest(@NotNull Long activoId, @NotNull TipoMovimientoInversion tipo,
                                              @NotNull LocalDate fecha, @NotNull BigDecimal cantidad,
                                              @NotNull BigDecimal precio) {
    }
}
