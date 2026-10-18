package com.tfg.finanzas.service;

import com.tfg.finanzas.model.Categoria;
import com.tfg.finanzas.model.Movimiento;
import com.tfg.finanzas.model.ReglaCategorizacion;
import com.tfg.finanzas.model.TipoCategoria;
import com.tfg.finanzas.repository.MovimientoRepository;
import com.tfg.finanzas.repository.ReglaCategorizacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Categorizacion por reglas simples: la primera regla (por prioridad ascendente)
 * cuyo patron aparezca como subcadena de la descripcion, sin distinguir mayusculas
 * ni acentos, gana. No es un motor de expresiones regulares a proposito: el
 * objetivo es la version mas simple posible (palabra clave -> categoria).
 * <p>
 * Una regla solo es aplicable si el tipo de su categoria es compatible con el
 * signo del movimiento: una categoria de GASTO no se asigna a un importe positivo
 * (un reembolso) ni una de INGRESO a uno negativo; NEUTRO vale para ambos.
 */
@Service
@RequiredArgsConstructor
public class CategorizacionService {

    private final ReglaCategorizacionRepository reglaCategorizacionRepository;
    private final MovimientoRepository movimientoRepository;

    public Categoria categorizar(String descripcion, BigDecimal importe) {
        if (descripcion == null || descripcion.isBlank()) {
            return null;
        }
        return categorizar(descripcion, importe, reglaCategorizacionRepository.findAllByOrderByPrioridadAsc());
    }

    private Categoria categorizar(String descripcion, BigDecimal importe, List<ReglaCategorizacion> reglas) {
        String normalizada = normalizar(descripcion);
        for (ReglaCategorizacion regla : reglas) {
            if (normalizada.contains(normalizar(regla.getPatron())) && esCompatible(regla.getCategoria(), importe)) {
                return regla.getCategoria();
            }
        }
        return null;
    }

    /** Asigna categoria a los movimientos que no tienen ninguna. */
    @Transactional
    public int recategorizarPendientes() {
        List<ReglaCategorizacion> reglas = reglaCategorizacionRepository.findAllByOrderByPrioridadAsc();
        int actualizados = 0;
        for (Movimiento movimiento : movimientoRepository.findByCategoriaIsNull()) {
            Categoria categoria = categorizar(movimiento.getDescripcionOriginal(), movimiento.getImporte(), reglas);
            if (categoria != null) {
                movimiento.setCategoria(categoria);
                actualizados++;
            }
        }
        return actualizados;
    }

    /**
     * Recalcula la categoria de todos los movimientos que NO se hayan categorizado
     * a mano, con las reglas actuales: corrige los que una regla antigua o un error
     * dejo mal puestos. Devuelve cuantos han cambiado de categoria.
     */
    @Transactional
    public int recategorizarAutomaticas() {
        List<ReglaCategorizacion> reglas = reglaCategorizacionRepository.findAllByOrderByPrioridadAsc();
        int cambiados = 0;
        for (Movimiento movimiento : movimientoRepository.findAll()) {
            if (movimiento.isCategoriaManual()) {
                continue;
            }
            Categoria nueva = categorizar(movimiento.getDescripcionOriginal(), movimiento.getImporte(), reglas);
            Long idActual = movimiento.getCategoria() != null ? movimiento.getCategoria().getId() : null;
            Long idNueva = nueva != null ? nueva.getId() : null;
            if (!java.util.Objects.equals(idActual, idNueva)) {
                movimiento.setCategoria(nueva);
                cambiados++;
            }
        }
        return cambiados;
    }

    private boolean esCompatible(Categoria categoria, BigDecimal importe) {
        if (importe == null || categoria.getTipo() == TipoCategoria.NEUTRO) {
            return true;
        }
        return categoria.getTipo() == TipoCategoria.GASTO ? importe.signum() <= 0 : importe.signum() >= 0;
    }

    static String normalizar(String texto) {
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT);
    }
}
