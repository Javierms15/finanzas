package com.tfg.finanzas.web;

import com.tfg.finanzas.dto.MovimientoDtos.AgregacionMensual;
import com.tfg.finanzas.dto.MovimientoDtos.CambiarCategoriaRequest;
import com.tfg.finanzas.model.Categoria;
import com.tfg.finanzas.model.Movimiento;
import com.tfg.finanzas.repository.CategoriaRepository;
import com.tfg.finanzas.service.CategorizacionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.tfg.finanzas.model.Movimiento;
import com.tfg.finanzas.repository.MovimientoRepository;
import com.tfg.finanzas.service.AgregacionMensualService;
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
    private final CategoriaRepository categoriaRepository;
    private final CategorizacionService categorizacionService;

    @GetMapping
    public List<Movimiento> listar(@PathVariable Long cuentaId) {
        return movimientoRepository.findByCuentaId(cuentaId);
    }

    @GetMapping("/agregacion-mensual")
    public List<AgregacionMensual> agregacionMensual(@PathVariable Long cuentaId) {
        return agregacionMensualService.porCuenta(cuentaId);
    }

    /**
     * Categoria elegida a mano: la recategorizacion automatica ya no la toca.
     * categoriaId null = quitar la eleccion manual y volver a la categoria que den las reglas.
     */
    @PatchMapping("/{movimientoId}/categoria")
    @Transactional
    public Movimiento cambiarCategoria(@PathVariable Long cuentaId, @PathVariable Long movimientoId,
                                        @RequestBody CambiarCategoriaRequest request) {
        Movimiento movimiento = movimientoRepository.findById(movimientoId)
                .filter(m -> m.getCuenta().getId().equals(cuentaId))
                .orElseThrow(() -> new IllegalArgumentException("Movimiento no encontrado: " + movimientoId));
        if (request.categoriaId() == null) {
            movimiento.setCategoria(categorizacionService.categorizar(movimiento.getDescripcionOriginal(), movimiento.getImporte()));
            movimiento.setCategoriaManual(false);
        } else {
            Categoria categoria = categoriaRepository.findById(request.categoriaId())
                    .orElseThrow(() -> new IllegalArgumentException("Categoria no encontrada: " + request.categoriaId()));
            movimiento.setCategoria(categoria);
            movimiento.setCategoriaManual(true);
        }
        return movimientoRepository.save(movimiento);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> manejarError(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
