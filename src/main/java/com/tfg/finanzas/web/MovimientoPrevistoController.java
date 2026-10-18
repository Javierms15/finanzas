package com.tfg.finanzas.web;

import com.tfg.finanzas.dto.PrevistoDtos.ActualizarCumplidoRequest;
import com.tfg.finanzas.dto.PrevistoDtos.CrearPrevistoRequest;
import com.tfg.finanzas.dto.PrevistoDtos.Response;
import com.tfg.finanzas.model.MovimientoPrevisto;
import com.tfg.finanzas.model.Usuario;
import com.tfg.finanzas.repository.MovimientoPrevistoRepository;
import com.tfg.finanzas.repository.UsuarioRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/usuarios/{usuarioId}/previstos")
@RequiredArgsConstructor
public class MovimientoPrevistoController {

    private final MovimientoPrevistoRepository movimientoPrevistoRepository;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public List<Response> listar(@PathVariable Long usuarioId) {
        return movimientoPrevistoRepository.findByUsuarioIdOrderByFechaAsc(usuarioId).stream()
                .map(Response::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<Response> crear(@PathVariable Long usuarioId, @RequestBody @Valid CrearPrevistoRequest request) {
        if (request.importe().signum() == 0) {
            throw new IllegalArgumentException("El importe no puede ser 0");
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + usuarioId));
        MovimientoPrevisto previsto = MovimientoPrevisto.builder()
                .usuario(usuario)
                .fecha(request.fecha())
                .importe(request.importe())
                .descripcion(request.descripcion().trim())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(Response.from(movimientoPrevistoRepository.save(previsto)));
    }

    @PatchMapping("/{previstoId}")
    public Response actualizarCumplido(@PathVariable Long usuarioId, @PathVariable Long previstoId,
                                        @RequestBody @Valid ActualizarCumplidoRequest request) {
        MovimientoPrevisto previsto = buscarDeUsuario(usuarioId, previstoId);
        previsto.setCumplido(request.cumplido());
        return Response.from(movimientoPrevistoRepository.save(previsto));
    }

    @DeleteMapping("/{previstoId}")
    public ResponseEntity<Void> eliminar(@PathVariable Long usuarioId, @PathVariable Long previstoId) {
        movimientoPrevistoRepository.delete(buscarDeUsuario(usuarioId, previstoId));
        return ResponseEntity.noContent().build();
    }

    private MovimientoPrevisto buscarDeUsuario(Long usuarioId, Long previstoId) {
        MovimientoPrevisto previsto = movimientoPrevistoRepository.findById(previstoId)
                .orElseThrow(() -> new IllegalArgumentException("Movimiento previsto no encontrado: " + previstoId));
        if (!previsto.getUsuario().getId().equals(usuarioId)) {
            throw new IllegalArgumentException("El movimiento previsto no pertenece al usuario indicado");
        }
        return previsto;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> manejarError(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
