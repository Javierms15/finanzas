package com.tfg.finanzas.web;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Respuesta de error uniforme {"message": "..."} para que el frontend pueda mostrar el motivo.
 * Sin esto, Spring Boot solo incluye el mensaje en modo desarrollo (devtools): empaquetado en
 * un .jar lo omite y la pantalla se quedaria sin saber por que se rechazo algo.
 */
@RestControllerAdvice
public class ErroresAdvice {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validacion(MethodArgumentNotValidException e) {
        String mensaje = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining(". "));
        return ResponseEntity.badRequest().body(Map.of("message", mensaje));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> estado(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", e.getReason() != null ? e.getReason() : "Error"));
    }
}
