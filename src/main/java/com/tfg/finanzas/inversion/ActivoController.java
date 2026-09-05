package com.tfg.finanzas.inversion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/activos")
@RequiredArgsConstructor
public class ActivoController {

    private final ActivoRepository activoRepository;

    @GetMapping
    public List<Activo> listar() {
        return activoRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<Activo> crear(@RequestBody @Valid CrearActivoRequest request) {
        Activo activo = Activo.builder()
                .ticker(request.ticker())
                .nombre(request.nombre())
                .tipo(request.tipo())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(activoRepository.save(activo));
    }

    public record CrearActivoRequest(@NotBlank String ticker, @NotBlank String nombre, @NotNull TipoActivo tipo) {
    }
}
