package com.tfg.finanzas.movimiento;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/cuentas/{cuentaId}/movimientos")
@RequiredArgsConstructor
public class MovimientoController {

    private final MovimientoRepository movimientoRepository;
    private final AgregacionMensualService agregacionMensualService;

    @GetMapping
    public List<Movimiento> listar(@PathVariable Long cuentaId) {
        return movimientoRepository.findByCuentaId(cuentaId);
    }

    @GetMapping("/agregacion-mensual")
    public List<AgregacionMensual> agregacionMensual(@PathVariable Long cuentaId) {
        return agregacionMensualService.porCuenta(cuentaId);
    }
}
