package com.tfg.finanzas.categoria;

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
@RequestMapping("/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaRepository categoriaRepository;
    private final CategorizacionService categorizacionService;

    @GetMapping
    public List<Categoria> listar() {
        return categoriaRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<Categoria> crear(@RequestBody @Valid CrearCategoriaRequest request) {
        Categoria categoria = Categoria.builder()
                .nombre(request.nombre())
                .tipo(request.tipo())
                .color(request.color())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(categoriaRepository.save(categoria));
    }

    @PostMapping("/recategorizar")
    public ResponseEntity<Integer> recategorizar() {
        return ResponseEntity.ok(categorizacionService.recategorizarPendientes());
    }

    public record CrearCategoriaRequest(@NotBlank String nombre, @NotNull TipoCategoria tipo, String color) {
    }
}
