package com.tfg.finanzas.web;

import com.tfg.finanzas.dto.CategoriaDtos.CrearCategoriaRequest;
import com.tfg.finanzas.dto.CategoriaDtos.ResultadoSugeridas;
import com.tfg.finanzas.model.Categoria;
import com.tfg.finanzas.repository.CategoriaRepository;
import com.tfg.finanzas.service.CategorizacionService;
import com.tfg.finanzas.service.CategoriasSugeridasService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaRepository categoriaRepository;
    private final CategorizacionService categorizacionService;
    private final CategoriasSugeridasService categoriasSugeridasService;

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

    /**
     * Por defecto solo categoriza los movimientos que no tienen categoria; con
     * todas=true recalcula tambien los categorizados automaticamente (nunca los
     * que el usuario haya puesto a mano).
     */
    @PostMapping("/recategorizar")
    public ResponseEntity<Integer> recategorizar(@RequestParam(defaultValue = "false") boolean todas) {
        return ResponseEntity.ok(todas ? categorizacionService.recategorizarAutomaticas()
                : categorizacionService.recategorizarPendientes());
    }

    @PostMapping("/sugeridas")
    public ResponseEntity<ResultadoSugeridas> cargarSugeridas(@RequestParam Long usuarioId) {
        return ResponseEntity.ok(categoriasSugeridasService.cargar(usuarioId));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> manejarError(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
