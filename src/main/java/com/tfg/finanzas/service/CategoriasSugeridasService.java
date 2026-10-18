package com.tfg.finanzas.service;

import com.tfg.finanzas.dto.CategoriaDtos.ResultadoSugeridas;
import com.tfg.finanzas.model.Categoria;
import com.tfg.finanzas.model.ReglaCategorizacion;
import com.tfg.finanzas.model.TipoCategoria;
import com.tfg.finanzas.model.Usuario;
import com.tfg.finanzas.repository.CategoriaRepository;
import com.tfg.finanzas.repository.ReglaCategorizacionRepository;
import com.tfg.finanzas.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.tfg.finanzas.model.TipoCategoria.GASTO;
import static com.tfg.finanzas.model.TipoCategoria.INGRESO;
import static com.tfg.finanzas.model.TipoCategoria.NEUTRO;

/**
 * Carga un juego de categorias y reglas razonables para extractos de bancos
 * espanoles (ING, Trade Republic...). Es idempotente: no duplica lo que ya
 * existe, asi que se puede ejecutar varias veces.
 * <p>
 * El ORDEN de la lista es la prioridad: lo especifico va antes que lo generico
 * (p. ej. "Suscripciones" antes que "Compras", porque ING etiqueta Google One y
 * Apple como "Compras Electronica"). Las reglas sugeridas empiezan en la
 * prioridad 100, por debajo de cualquier regla puesta a mano con prioridad menor.
 * <p>
 * Para las transferencias a uno mismo se necesita el nombre del usuario: el
 * extracto dice "Transferencia emitida a <nombre completo>".
 */
@Service
@RequiredArgsConstructor
public class CategoriasSugeridasService {

    private static final int PRIORIDAD_BASE = 100;

    private final CategoriaRepository categoriaRepository;
    private final ReglaCategorizacionRepository reglaCategorizacionRepository;
    private final UsuarioRepository usuarioRepository;

    private record Definicion(String nombre, TipoCategoria tipo, List<String> patrones) {
    }

    @Transactional
    public ResultadoSugeridas cargar(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + usuarioId));
        String nombre = usuario.getNombre().trim();

        Set<String> reglasExistentes = new HashSet<>();
        for (ReglaCategorizacion r : reglaCategorizacionRepository.findAll()) {
            reglasExistentes.add(r.getCategoria().getId() + "|" + CategorizacionService.normalizar(r.getPatron()));
        }

        int categoriasCreadas = 0;
        int reglasCreadas = 0;
        int prioridad = PRIORIDAD_BASE;
        for (Definicion def : definiciones(nombre)) {
            boolean nueva = categoriaRepository.findFirstByNombreIgnoreCase(def.nombre()).isEmpty();
            Categoria categoria = categoriaRepository.findFirstByNombreIgnoreCase(def.nombre())
                    .orElseGet(() -> categoriaRepository.save(
                            Categoria.builder().nombre(def.nombre()).tipo(def.tipo()).build()));
            if (nueva) {
                categoriasCreadas++;
            }
            for (String patron : def.patrones()) {
                prioridad++;
                if (reglasExistentes.add(categoria.getId() + "|" + CategorizacionService.normalizar(patron))) {
                    reglaCategorizacionRepository.save(ReglaCategorizacion.builder()
                            .categoria(categoria).patron(patron).prioridad(prioridad).build());
                    reglasCreadas++;
                }
            }
        }
        return new ResultadoSugeridas(categoriasCreadas, reglasCreadas);
    }

    private List<Definicion> definiciones(String nombreUsuario) {
        return List.of(
                // --- NEUTRO: mueve dinero, no es ni ingreso ni gasto real
                new Definicion("Transferencias propias", NEUTRO, List.of(
                        "emitida a " + nombreUsuario, "recibida de " + nombreUsuario, "traspaso", "transferencia intern",
                        "movimientos excluidos")),
                new Definicion("Aportaciones a inversión", NEUTRO, List.of("savings plan execution", "operar:")),
                // --- INGRESO
                new Definicion("Nómina", INGRESO, List.of("nomina recibida", "ingreso nomina", "nomina o pension")),
                new Definicion("Intereses y bonificaciones", INGRESO, List.of(
                        "intereses a su favor", "interest payment", "saveback", "bonificacion")),
                new Definicion("Devoluciones de impuestos", INGRESO, List.of("devoluciones tributarias", "agencia tributaria")),
                new Definicion("Bizum recibido", INGRESO, List.of("bizum recibido", "ingreso bizum")),
                // --- GASTO (lo especifico primero)
                new Definicion("Suscripciones", GASTO, List.of(
                        "suscripciones", "google one", "apple.combill", "netflix", "spotify", "hbo", "disney", "prime video",
                        "icloud")),
                new Definicion("Supermercado", GASTO, List.of(
                        "mercadona", "carrefour", "lidl", "eroski", "alcampo", "supermercad")),
                new Definicion("Restaurantes y cafeterías", GASTO, List.of(
                        "cafeterias y restaurantes", "restaurante", "cafeteria", "cafelito", "glovo", "uber eats", "just eat",
                        "mcdonald", "burger king", "telepizza")),
                new Definicion("Transporte", GASTO, List.of(
                        "taxi", "carsharing", "vehiculo y transporte", "cabify", "movilidad", "gasolinera", "repsol", "cepsa",
                        "parking", "renfe")),
                new Definicion("Ocio y viajes", GASTO, List.of(
                        "ocio y viajes", "hertz", "booking", "airbnb", "ryanair", "vueling", "cinesa", "yelmo")),
                new Definicion("Salud", GASTO, List.of("farmacia", "herborister", "clinica", "dentista", "optica")),
                new Definicion("Efectivo", GASTO, List.of("cajeros", "reintegro efectivo")),
                new Definicion("Impuestos y comisiones", GASTO, List.of("impuestos retenidos", "comision")),
                new Definicion("Bizum enviado", GASTO, List.of("bizum enviado", "gasto bizum")),
                new Definicion("Compras", GASTO, List.of(
                        "compras", "amazon", "aliexpress", "ikea", "pccomponentes", "transaccion con tarjeta")),
                new Definicion("Otros ingresos", INGRESO, List.of("otros ingresos")));
    }
}
