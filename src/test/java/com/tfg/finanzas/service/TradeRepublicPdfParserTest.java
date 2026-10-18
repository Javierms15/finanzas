package com.tfg.finanzas.service;

import com.tfg.finanzas.dto.ImportacionDtos.MovimientoImportado;
import com.tfg.finanzas.support.PdfSintetico;
import com.tfg.finanzas.support.PdfSintetico.Texto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TradeRepublicPdfParserTest {

    // Misma separacion que usa el extracto real entre numero y simbolo: un espacio de no separacion (NBSP)
    static final String EUR = " €";

    final TradeRepublicPdfParser parser = new TradeRepublicPdfParser();

    /** Una fila del extracto: fecha en dos lineas y tipo/descripcion/entrada/salida/balance en sus columnas. */
    private static void fila(List<Texto> t, float y, String diaMes, String anio, String tipo, String descripcion,
                             String entrada, String salida, String balance) {
        t.add(new Texto(70, y, diaMes));
        t.add(new Texto(70, y - 9, anio));
        t.add(new Texto(100, y, tipo));
        t.add(new Texto(160, y, descripcion));
        if (entrada != null) {
            t.add(new Texto(405, y, entrada + EUR));
        }
        if (salida != null) {
            t.add(new Texto(435, y, salida + EUR));
        }
        t.add(new Texto(470, y, balance + EUR));
    }

    private static List<Texto> paginaConCabecera() {
        List<Texto> t = new ArrayList<>();
        t.add(new Texto(50, 800, "TRADE REPUBLIC"));
        t.add(new Texto(50, 788, "TRANSACCIONES DE CUENTA"));
        t.add(new Texto(70, 760, "FECHA"));
        t.add(new Texto(100, 760, "TIPO"));
        t.add(new Texto(160, 760, "DESCRIPCIÓN"));
        t.add(new Texto(405, 760, "ENTRADA DE"));
        t.add(new Texto(435, 760, "SALIDA DE"));
        t.add(new Texto(470, 760, "BALANCE"));
        return t;
    }

    @Test
    void soportaElExtractoDeMovimientosPeroNoElDeValores() {
        byte[] movimientos = PdfSintetico.deLineas("TRADE REPUBLIC", "TRANSACCIONES DE CUENTA");
        byte[] valores = PdfSintetico.deLineas("TRADE REPUBLIC", "EXTRACTO DE LA CUENTA DE VALORES");

        assertThat(parser.soporta("extracto.pdf", movimientos)).isTrue();
        assertThat(parser.soporta("extracto.pdf", valores)).isFalse();
        assertThat(parser.soporta("extracto.csv", movimientos)).isFalse();
    }

    @Test
    void distingueEntradasDeSalidasPorLaColumna() {
        List<Texto> t = paginaConCabecera();
        fila(t, 740, "10 sept", "2026", "Transferencia", "Ingreso inicial", "1.500,00", null, "1.500,00");
        fila(t, 710, "11 sept", "2026", "Tarjeta", "Compra Mercadona", null, "23,45", "1.476,55");
        t.add(new Texto(70, 600, "RESUMEN DEL BALANCE"));

        List<MovimientoImportado> movs = parser.parsear(PdfSintetico.deTextos(List.of(t)));

        assertThat(movs).hasSize(2);
        assertThat(movs.get(0).fecha()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(movs.get(0).importe()).isEqualByComparingTo("1500.00");
        assertThat(movs.get(0).descripcionOriginal()).isEqualTo("Transferencia: Ingreso inicial");
        assertThat(movs.get(1).fecha()).isEqualTo(LocalDate.of(2026, 9, 11));
        assertThat(movs.get(1).importe()).isEqualByComparingTo("-23.45");
        assertThat(movs.get(1).descripcionOriginal()).isEqualTo("Tarjeta: Compra Mercadona");
    }

    @Test
    void ignoraLoQueHayDespuesDelResumenYLasNotas() {
        List<Texto> t = paginaConCabecera();
        fila(t, 740, "02 oct", "2026", "Tarjeta", "Cafe", null, "1,80", "98,20");
        t.add(new Texto(70, 600, "NOTAS SOBRE EL ESTADO DE CUENTA"));
        fila(t, 580, "03 oct", "2026", "Tarjeta", "No deberia leerse", null, "9,99", "88,21");

        assertThat(parser.parsear(PdfSintetico.deTextos(List.of(t)))).hasSize(1);
    }

    @Test
    void unaFilaConMesNoReconocidoSeDescartaSinRomperElResto() {
        List<Texto> t = paginaConCabecera();
        fila(t, 740, "10 xyz", "2026", "Tarjeta", "Mes raro", null, "5,00", "95,00");
        fila(t, 710, "11 nov", "2026", "Tarjeta", "Mes bueno", null, "2,00", "93,00");
        t.add(new Texto(70, 600, "RESUMEN DEL BALANCE"));

        List<MovimientoImportado> movs = parser.parsear(PdfSintetico.deTextos(List.of(t)));

        assertThat(movs).extracting(MovimientoImportado::descripcionOriginal).containsExactly("Tarjeta: Mes bueno");
    }

    @Test
    void leeVariasPaginas() {
        List<Texto> p1 = paginaConCabecera();
        fila(p1, 740, "10 ene", "2026", "Tarjeta", "Uno", null, "1,00", "99,00");
        List<Texto> p2 = paginaConCabecera();
        fila(p2, 740, "11 dic", "2026", "Tarjeta", "Dos", null, "2,00", "97,00");

        List<MovimientoImportado> movs = parser.parsear(PdfSintetico.deTextos(List.of(p1, p2)));

        assertThat(movs).extracting(MovimientoImportado::fecha)
                .containsExactly(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 12, 11));
    }
}
