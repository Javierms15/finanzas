package com.tfg.finanzas.web;

import com.tfg.finanzas.dto.ReglaCategorizacionDtos.CrearReglaRequest;
import com.tfg.finanzas.model.Categoria;
import com.tfg.finanzas.model.ReglaCategorizacion;
import com.tfg.finanzas.repository.CategoriaRepository;
import com.tfg.finanzas.repository.ReglaCategorizacionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/reglas-categorizacion")
@RequiredArgsConstructor
public class ReglaCategorizacionController {

    private final ReglaCategorizacionRepository reglaCategorizacionRepository;
    private final CategoriaRepository categoriaRepository;

    @GetMapping
    public List<ReglaCategorizacion> listar() {
        return reglaCategorizacionRepository.findAllByOrderByPrioridadAsc();
    }

    @PostMapping
    public ResponseEntity<ReglaCategorizacion> crear(@RequestBody @Valid CrearReglaRequest request) {
        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new IllegalArgumentException("Categoria no encontrada: " + request.categoriaId()));
        ReglaCategorizacion regla = ReglaCategorizacion.builder()
                .categoria(categoria)
                .patron(request.patron())
                .prioridad(request.prioridad())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(reglaCategorizacionRepository.save(regla));
    }

    @DeleteMapping("/{reglaId}")
    public ResponseEntity<Void> eliminar(@PathVariable Long reglaId) {
        if (!reglaCategorizacionRepository.existsById(reglaId)) {
            throw new IllegalArgumentException("Regla no encontrada: " + reglaId);
        }
        reglaCategorizacionRepository.deleteById(reglaId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> manejarCategoriaNoEncontrada(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
