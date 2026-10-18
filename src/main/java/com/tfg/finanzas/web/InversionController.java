package com.tfg.finanzas.web;

import com.tfg.finanzas.dto.InversionDtos.PosicionValorada;
import com.tfg.finanzas.dto.InversionDtos.ProyeccionInversion;
import com.tfg.finanzas.dto.InversionDtos.RegistrarMovimientoRequest;
import com.tfg.finanzas.dto.InversionDtos.ResultadoImportacionPosiciones;
import com.tfg.finanzas.model.Posicion;
import com.tfg.finanzas.service.ImportacionException;
import com.tfg.finanzas.service.ImportacionPosicionesService;
import com.tfg.finanzas.service.InversionService;
import com.tfg.finanzas.service.PosicionService;
import com.tfg.finanzas.service.ProyeccionInversionService;
import jakarta.validation.Valid;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/usuarios/{usuarioId}/inversion")
@RequiredArgsConstructor
public class InversionController {

    private final InversionService inversionService;
    private final PosicionService posicionService;
    private final ProyeccionInversionService proyeccionInversionService;
    private final ImportacionPosicionesService importacionPosicionesService;

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

    @PostMapping(value = "/posiciones/importar", consumes = "multipart/form-data")
    public ResponseEntity<ResultadoImportacionPosiciones> importarPosiciones(@PathVariable Long usuarioId,
                                                                              @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(
                    importacionPosicionesService.importar(usuarioId, file.getOriginalFilename(), file.getBytes()));
        } catch (IOException e) {
            throw new ImportacionException("No se ha podido leer el fichero subido", e);
        }
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

    @ExceptionHandler(ImportacionException.class)
    public ResponseEntity<String> manejarErrorImportacion(ImportacionException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
