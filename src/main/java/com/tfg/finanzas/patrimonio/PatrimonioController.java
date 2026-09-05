package com.tfg.finanzas.patrimonio;

import com.tfg.finanzas.movimiento.AgregacionMensual;
import com.tfg.finanzas.movimiento.AgregacionMensualService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/usuarios/{usuarioId}/patrimonio")
@RequiredArgsConstructor
public class PatrimonioController {

    private final PatrimonioService patrimonioService;
    private final AgregacionMensualService agregacionMensualService;
    private final PatrimonioProyeccionService patrimonioProyeccionService;

    @PostMapping("/snapshots")
    public ResponseEntity<SnapshotPatrimonio> generarSnapshot(@PathVariable Long usuarioId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(patrimonioService.generarSnapshot(usuarioId));
    }

    @GetMapping("/snapshots")
    public List<SnapshotPatrimonio> historico(@PathVariable Long usuarioId) {
        return patrimonioService.historico(usuarioId);
    }

    @GetMapping("/agregacion-mensual")
    public List<AgregacionMensual> agregacionMensual(@PathVariable Long usuarioId) {
        return agregacionMensualService.porUsuario(usuarioId);
    }

    @GetMapping("/proyeccion")
    public ProyeccionEfectivo proyeccion(@PathVariable Long usuarioId,
                                          @RequestParam(defaultValue = "3") int meses) {
        return patrimonioProyeccionService.proyectar(usuarioId, meses);
    }

    @GetMapping("/proyeccion/simular")
    public SimulacionAjusteCategoria simular(@PathVariable Long usuarioId,
                                              @RequestParam(defaultValue = "3") int meses,
                                              @RequestParam Long categoriaId,
                                              @RequestParam BigDecimal ajuste) {
        return patrimonioProyeccionService.simular(usuarioId, meses, categoriaId, ajuste);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> manejarUsuarioNoEncontrado(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
