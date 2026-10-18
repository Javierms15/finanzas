package com.tfg.finanzas.web;

import com.tfg.finanzas.service.ActividadCliente;
import com.tfg.finanzas.service.ActividadCliente.Estado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints publicos (sin sesion) para que el lanzador de escritorio sepa si la web sigue abierta.
 * Solo exponen tiempos transcurridos; el servidor ademas solo escucha en 127.0.0.1.
 */
@RestController
@RequestMapping("/sistema")
@RequiredArgsConstructor
public class SistemaController {

    private final ActividadCliente actividadCliente;

    @PostMapping("/latido")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void latido() {
        actividadCliente.latido();
    }

    @PostMapping("/cierre")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cierre() {
        actividadCliente.cierre();
    }

    @GetMapping("/actividad")
    public Estado actividad() {
        return actividadCliente.estado();
    }
}
