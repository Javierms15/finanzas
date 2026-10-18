package com.tfg.finanzas.service;

import com.tfg.finanzas.dto.InversionDtos.PosicionImportada;
import com.tfg.finanzas.model.TipoActivo;
import com.tfg.finanzas.support.PdfSintetico;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TradeRepublicPosicionesPdfParserTest {

    final TradeRepublicPosicionesPdfParser parser = new TradeRepublicPosicionesPdfParser();

    private byte[] pdf() {
        return PdfSintetico.deLineas(
                "TRADE REPUBLIC",
                "EXTRACTO DE LA CUENTA DE VALORES",
                "POSICIONES",
                "10 tít. ISHARES CORE MSCI WORLD UCITS ETF",
                "ISIN: IE00B4L5Y983",
                "Cuenta de valores en Alemania",
                "98,45",
                "15.10.2026",
                "1.234,50 tít. NVIDIA CORP.",
                "ISIN: US67066G1040",
                "175,2",
                "15.10.2026",
                "2,5 tít. AMUNDI PHYSICAL",
                "METALS ETC",
                "ISIN: IE00BVFZGC04",
                "País de custodia: Alemania",
                "62,10",
                "NÚMERO DE POSICIONES: 3",
                "5 tít. NO DEBE LEERSE",
                "ISIN: XX0000000000",
                "1,00");
    }

    @Test
    void soloSeReconoceElExtractoDeLaCuentaDeValores() {
        assertThat(parser.soporta("valores.pdf", pdf())).isTrue();
        assertThat(parser.soporta("valores.pdf", PdfSintetico.deLineas("TRANSACCIONES DE CUENTA"))).isFalse();
        assertThat(parser.soporta("valores.csv", pdf())).isFalse();
    }

    @Test
    void extraeCantidadPrecioIsinYNombre() {
        List<PosicionImportada> pos = parser.parsear(pdf());

        assertThat(pos).hasSize(3);
        PosicionImportada etf = pos.get(0);
        assertThat(etf.isin()).isEqualTo("IE00B4L5Y983");
        assertThat(etf.nombre()).isEqualTo("ISHARES CORE MSCI WORLD UCITS ETF");
        assertThat(etf.cantidad()).isEqualByComparingTo("10");
        assertThat(etf.precio()).isEqualByComparingTo("98.45");
    }

    @Test
    void interpretaMillaresYDecimalesEspanoles() {
        PosicionImportada nvidia = parser.parsear(pdf()).get(1);

        assertThat(nvidia.cantidad()).isEqualByComparingTo("1234.5");
        assertThat(nvidia.precio()).isEqualByComparingTo("175.2");
    }

    @Test
    void unNombreEnDosLineasSeUneYSeInfiereElTipo() {
        List<PosicionImportada> pos = parser.parsear(pdf());

        assertThat(pos.get(2).nombre()).isEqualTo("AMUNDI PHYSICAL METALS ETC");
        assertThat(pos.get(2).tipo()).isEqualTo(TipoActivo.ETF);
        assertThat(pos.get(0).tipo()).isEqualTo(TipoActivo.ETF);
        assertThat(pos.get(1).tipo()).isEqualTo(TipoActivo.ACCION);
    }

    @Test
    void sePareEnElContadorDePosiciones() {
        assertThat(parser.parsear(pdf())).noneMatch(p -> p.nombre().contains("NO DEBE"));
    }
}
