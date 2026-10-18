package com.tfg.finanzas.web;

import com.tfg.finanzas.dto.ImportacionDtos.ResultadoImportacion;
import com.tfg.finanzas.model.CuentaBancaria;
import com.tfg.finanzas.repository.CuentaBancariaRepository;
import com.tfg.finanzas.service.ImportacionException;
import com.tfg.finanzas.service.ImportacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/cuentas/{cuentaId}/importar")
@RequiredArgsConstructor
public class ImportacionController {

    private final ImportacionService importacionService;
    private final CuentaBancariaRepository cuentaBancariaRepository;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ResultadoImportacion> importar(@PathVariable Long cuentaId,
                                                           @RequestParam("file") MultipartFile file) {
        CuentaBancaria cuenta = cuentaBancariaRepository.findById(cuentaId)
                .orElseThrow(() -> new ImportacionException("Cuenta no encontrada: " + cuentaId));
        try {
            return ResponseEntity.ok(importacionService.importar(cuenta, file.getOriginalFilename(), file.getBytes()));
        } catch (IOException e) {
            throw new ImportacionException("No se ha podido leer el fichero subido", e);
        }
    }

    @ExceptionHandler(ImportacionException.class)
    public ResponseEntity<String> manejarErrorImportacion(ImportacionException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
