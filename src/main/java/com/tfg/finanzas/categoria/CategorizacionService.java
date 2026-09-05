package com.tfg.finanzas.categoria;

import com.tfg.finanzas.movimiento.Movimiento;
import com.tfg.finanzas.movimiento.MovimientoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Categorizacion por reglas simples: la primera regla (por prioridad ascendente)
 * cuyo patron aparezca como subcadena de la descripcion, sin distinguir mayusculas
 * ni acentos, gana. No es un motor de expresiones regulares a proposito: el
 * objetivo de este paso es la version mas simple posible (palabra clave -> categoria).
 */
@Service
@RequiredArgsConstructor
public class CategorizacionService {

    private final ReglaCategorizacionRepository reglaCategorizacionRepository;
    private final MovimientoRepository movimientoRepository;

    public Categoria categorizar(String descripcion) {
        if (descripcion == null || descripcion.isBlank()) {
            return null;
        }
        String normalizada = normalizar(descripcion);
        for (ReglaCategorizacion regla : reglaCategorizacionRepository.findAllByOrderByPrioridadAsc()) {
            if (normalizada.contains(normalizar(regla.getPatron()))) {
                return regla.getCategoria();
            }
        }
        return null;
    }

    @Transactional
    public int recategorizarPendientes() {
        List<Movimiento> pendientes = movimientoRepository.findByCategoriaIsNull();
        int actualizados = 0;
        for (Movimiento movimiento : pendientes) {
            Categoria categoria = categorizar(movimiento.getDescripcionOriginal());
            if (categoria != null) {
                movimiento.setCategoria(categoria);
                actualizados++;
            }
        }
        return actualizados;
    }

    private String normalizar(String texto) {
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT);
    }
}
