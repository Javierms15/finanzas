package com.tfg.finanzas.service;

import com.tfg.finanzas.model.Categoria;
import com.tfg.finanzas.model.Movimiento;
import com.tfg.finanzas.model.ReglaCategorizacion;
import com.tfg.finanzas.model.TipoCategoria;
import com.tfg.finanzas.repository.MovimientoRepository;
import com.tfg.finanzas.repository.ReglaCategorizacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategorizacionServiceTest {

    @Mock
    ReglaCategorizacionRepository reglas;
    @Mock
    MovimientoRepository movimientos;

    CategorizacionService servicio;

    Categoria supermercado = categoria(1L, "Supermercado", TipoCategoria.GASTO);
    Categoria nomina = categoria(2L, "Nomina", TipoCategoria.INGRESO);
    Categoria traspasos = categoria(3L, "Traspasos", TipoCategoria.NEUTRO);

    @BeforeEach
    void preparar() {
        servicio = new CategorizacionService(reglas, movimientos);
    }

    private static Categoria categoria(Long id, String nombre, TipoCategoria tipo) {
        return Categoria.builder().id(id).nombre(nombre).tipo(tipo).build();
    }

    private static ReglaCategorizacion regla(Categoria c, String patron, int prioridad) {
        return ReglaCategorizacion.builder().categoria(c).patron(patron).prioridad(prioridad).build();
    }

    private static Movimiento movimiento(String descripcion, String importe, Categoria categoria, boolean manual) {
        return Movimiento.builder().descripcionOriginal(descripcion).importe(new BigDecimal(importe))
                .categoria(categoria).categoriaManual(manual).build();
    }

    @Test
    void ignoraMayusculasYAcentos() {
        when(reglas.findAllByOrderByPrioridadAsc()).thenReturn(List.of(regla(supermercado, "mercadona", 1)));
        assertThat(servicio.categorizar("COMPRA MERCADONA S.A.", new BigDecimal("-12.30"))).isSameAs(supermercado);

        when(reglas.findAllByOrderByPrioridadAsc()).thenReturn(List.of(regla(nomina, "nómina", 1)));
        assertThat(servicio.categorizar("Transferencia NOMINA octubre", new BigDecimal("1500"))).isSameAs(nomina);
    }

    @Test
    void gananLasReglasDeMenorPrioridadNumerica() {
        when(reglas.findAllByOrderByPrioridadAsc()).thenReturn(List.of(
                regla(traspasos, "bizum", 1), regla(supermercado, "bizum mercadona", 2)));
        assertThat(servicio.categorizar("Bizum Mercadona", new BigDecimal("-5"))).isSameAs(traspasos);
    }

    @Test
    void unaCategoriaDeGastoNoSeAsignaAUnImportePositivo() {
        when(reglas.findAllByOrderByPrioridadAsc()).thenReturn(List.of(regla(supermercado, "mercadona", 1)));
        assertThat(servicio.categorizar("Devolucion Mercadona", new BigDecimal("8.00"))).isNull();
    }

    @Test
    void unaCategoriaDeIngresoNoSeAsignaAUnImporteNegativo() {
        when(reglas.findAllByOrderByPrioridadAsc()).thenReturn(List.of(regla(nomina, "empresa", 1)));
        assertThat(servicio.categorizar("Pago a empresa", new BigDecimal("-50"))).isNull();
    }

    @Test
    void laCategoriaNeutraValeParaCualquierSigno() {
        when(reglas.findAllByOrderByPrioridadAsc()).thenReturn(List.of(regla(traspasos, "traspaso", 1)));
        assertThat(servicio.categorizar("Traspaso", new BigDecimal("100"))).isSameAs(traspasos);
        assertThat(servicio.categorizar("Traspaso", new BigDecimal("-100"))).isSameAs(traspasos);
    }

    @Test
    void siUnaReglaNoEsCompatibleSeProbarLaSiguiente() {
        when(reglas.findAllByOrderByPrioridadAsc()).thenReturn(List.of(
                regla(supermercado, "mercadona", 1), regla(traspasos, "mercadona", 2)));
        assertThat(servicio.categorizar("Abono Mercadona", new BigDecimal("3"))).isSameAs(traspasos);
    }

    @Test
    void descripcionVaciaONulaNoCategoriza() {
        assertThat(servicio.categorizar(null, BigDecimal.TEN)).isNull();
        assertThat(servicio.categorizar("   ", BigDecimal.TEN)).isNull();
    }

    @Test
    void sinCoincidenciaDevuelveNull() {
        when(reglas.findAllByOrderByPrioridadAsc()).thenReturn(List.of(regla(supermercado, "mercadona", 1)));
        assertThat(servicio.categorizar("Gasolinera", new BigDecimal("-40"))).isNull();
    }

    @Test
    void recategorizarPendientesSoloRellenaLosQueTienenRegla() {
        Movimiento conRegla = movimiento("Mercadona", "-10", null, false);
        Movimiento sinRegla = movimiento("Desconocido", "-10", null, false);
        when(reglas.findAllByOrderByPrioridadAsc()).thenReturn(List.of(regla(supermercado, "mercadona", 1)));
        when(movimientos.findByCategoriaIsNull()).thenReturn(List.of(conRegla, sinRegla));

        assertThat(servicio.recategorizarPendientes()).isEqualTo(1);
        assertThat(conRegla.getCategoria()).isSameAs(supermercado);
        assertThat(sinRegla.getCategoria()).isNull();
    }

    @Test
    void recategorizarAutomaticasRespetaLasCategoriasManuales() {
        Movimiento manual = movimiento("Mercadona", "-10", traspasos, true);
        Movimiento automatico = movimiento("Mercadona", "-10", traspasos, false);
        Movimiento yaCorrecto = movimiento("Mercadona", "-10", supermercado, false);
        when(reglas.findAllByOrderByPrioridadAsc()).thenReturn(List.of(regla(supermercado, "mercadona", 1)));
        when(movimientos.findAll()).thenReturn(List.of(manual, automatico, yaCorrecto));

        assertThat(servicio.recategorizarAutomaticas()).isEqualTo(1);
        assertThat(manual.getCategoria()).isSameAs(traspasos);
        assertThat(automatico.getCategoria()).isSameAs(supermercado);
    }

    @Test
    void recategorizarAutomaticasQuitaLaCategoriaSiYaNingunaReglaAplica() {
        Movimiento huerfano = movimiento("Mercadona", "-10", supermercado, false);
        when(reglas.findAllByOrderByPrioridadAsc()).thenReturn(List.of());
        when(movimientos.findAll()).thenReturn(List.of(huerfano));

        assertThat(servicio.recategorizarAutomaticas()).isEqualTo(1);
        assertThat(huerfano.getCategoria()).isNull();
    }

    @Test
    void normalizarQuitaAcentosYPasaAMinusculas() {
        assertThat(CategorizacionService.normalizar("ÁÉÍÓÚ ñ Cañas")).isEqualTo("aeiou n canas");
    }
}
