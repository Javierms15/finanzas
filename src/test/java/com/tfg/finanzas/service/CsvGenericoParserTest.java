package com.tfg.finanzas.service;

import com.tfg.finanzas.dto.ImportacionDtos.MovimientoImportado;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvGenericoParserTest {

    final CsvGenericoParser parser = new CsvGenericoParser();

    private List<MovimientoImportado> parsear(String csv) {
        return parser.parsear(csv.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void soloSoportaFicherosCsv() {
        assertThat(parser.soporta("movimientos.CSV", new byte[0])).isTrue();
        assertThat(parser.soporta("extracto.pdf", new byte[0])).isFalse();
        assertThat(parser.soporta(null, new byte[0])).isFalse();
    }

    @Test
    void leeCsvConComasYFormatoIsoYPuntoDecimal() {
        var movs = parsear("fecha,descripcion,importe\n2026-03-05,Mercadona,-23.45\n2026-03-06,Nomina,1500.00\n");
        assertThat(movs).containsExactly(
                new MovimientoImportado(LocalDate.of(2026, 3, 5), new BigDecimal("-23.45"), "Mercadona"),
                new MovimientoImportado(LocalDate.of(2026, 3, 6), new BigDecimal("1500.00"), "Nomina"));
    }

    @Test
    void detectaElPuntoYComaYElFormatoEspanol() {
        var movs = parsear("Fecha;Concepto;Importe\n05/03/2026;Alquiler;-1.200,50\n");
        assertThat(movs).containsExactly(
                new MovimientoImportado(LocalDate.of(2026, 3, 5), new BigDecimal("-1200.50"), "Alquiler"));
    }

    @Test
    void aceptaFormatoAmericanoConMilesConComa() {
        var movs = parsear("date,description,amount\n2026-01-02,Salary,\"1,234.56\"\n");
        assertThat(movs.get(0).importe()).isEqualByComparingTo("1234.56");
    }

    @Test
    void aceptaFechaConGuiones() {
        var movs = parsear("fecha,detalle,importe\n05-03-2026,Cafe,-1.80\n");
        assertThat(movs.get(0).fecha()).isEqualTo(LocalDate.of(2026, 3, 5));
    }

    @Test
    void descartaLasFilasIlegiblesSinFallar() {
        var movs = parsear("fecha,descripcion,importe\nno-es-fecha,Roto,1\n2026-03-05,Bueno,-2\n2026-03-06,SinImporte,abc\n");
        assertThat(movs).extracting(MovimientoImportado::descripcionOriginal).containsExactly("Bueno");
    }

    @Test
    void cabeceraDesconocidaLanzaErrorDeImportacion() {
        assertThatThrownBy(() -> parsear("a,b,c\n1,2,3\n"))
                .isInstanceOf(ImportacionException.class)
                .hasMessageContaining("No se han reconocido las columnas");
    }

    @Test
    void ficheroVacioNoDevuelveMovimientos() {
        assertThat(parsear("")).isEmpty();
    }
}
