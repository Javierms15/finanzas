package com.tfg.finanzas.service;

import com.tfg.finanzas.dto.ImportacionDtos.MovimientoImportado;
import com.tfg.finanzas.dto.InversionDtos.PosicionImportada;
import com.tfg.finanzas.model.TipoActivo;
import com.tfg.finanzas.support.PdfSintetico;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Los tres parsers de ING: movimientos de una cuenta, extracto integrado (cuentas) y fondos del mismo extracto. */
class IngPdfParsersTest {

    final IngPdfParser movimientos = new IngPdfParser();
    final IngExtractoIntegradoPdfParser integrado = new IngExtractoIntegradoPdfParser();
    final IngFondosPdfParser fondos = new IngFondosPdfParser();

    // ---------------------------------------------------------------- Movimientos de la Cuenta

    private byte[] pdfMovimientos() {
        return PdfSintetico.deLineas(
                "Movimientos de la Cuenta",
                "Número de cuenta: ES00 0000 0000 0000",
                "Titular: PERSONA DE PRUEBA",
                "Fecha exportación: 01/10/2026",
                "F. VALOR CATEGORÍA DESCRIPCIÓN IMPORTE (€) SALDO (€)",
                "05/09/2026 Compra Mercadona -23.45 1,476.55",
                "06/09/2026 Transferencia recibida de",
                "Empresa SL Nomina 1,665.00 3,141.55",
                "07/09/2026 Recibo gimnasio -39.90 3,101.65");
    }

    @Test
    void ingMovimientosLosReconoceSoloSiTieneLaCabeceraDeIng() {
        assertThat(movimientos.soporta("mov.pdf", pdfMovimientos())).isTrue();
        assertThat(movimientos.soporta("mov.pdf", PdfSintetico.deLineas("Otro documento"))).isFalse();
        assertThat(movimientos.soporta("mov.csv", pdfMovimientos())).isFalse();
    }

    @Test
    void ingMovimientosExtraeFechaImporteConSignoYDescripcion() {
        List<MovimientoImportado> movs = movimientos.parsear(pdfMovimientos());

        assertThat(movs).hasSize(3);
        assertThat(movs.get(0)).isEqualTo(new MovimientoImportado(
                LocalDate.of(2026, 9, 5), new java.math.BigDecimal("-23.45"), "Compra Mercadona"));
        assertThat(movs.get(2).importe()).isEqualByComparingTo("-39.90");
    }

    @Test
    void ingMovimientosUneLasDescripcionesEnVariasLineasYLeeMillares() {
        MovimientoImportado m = movimientos.parsear(pdfMovimientos()).get(1);

        assertThat(m.importe()).isEqualByComparingTo("1665.00");
        assertThat(m.descripcionOriginal()).isEqualTo("Transferencia recibida de Empresa SL Nomina");
    }

    @Test
    void ingMovimientosDescartaUnRegistroSinImporteYSaldo() {
        byte[] pdf = PdfSintetico.deLineas(
                "Movimientos de la Cuenta", "F. VALOR CATEGORÍA DESCRIPCIÓN IMPORTE SALDO",
                "05/09/2026 Registro roto sin importes", "06/09/2026 Bueno -1.00 9.00");

        assertThat(movimientos.parsear(pdf)).extracting(MovimientoImportado::descripcionOriginal).containsExactly("Bueno");
    }

    // ---------------------------------------------------------------- Extracto Integrado: cuentas

    private byte[] pdfIntegrado() {
        return PdfSintetico.deLineas(
                "Extracto Integrado",
                "Septiembre 2026 Saldo Final 3.000,00 €",
                "• Cuentas",
                "Cuenta NÓMINA - IBAN: ES00 0000 0000 0000 0000",
                "05/09 05/09 Compra Mercadona -23,45 € 1.476,55 €",
                "06/09 06/09 Nómina empresa 1.665,00 € 3.141,55 €",
                "Cuenta NARANJA - IBAN: ES11 1111 1111 1111 1111",
                "30/09 30/09 Intereses a su favor 1,23 € 501,23 €",
                "• Fondos de Inversión",
                "Fondo Cartera Sostenible 5 - 1234567890.1 Saldo final 1.234,56 €",
                "01/09 10,5000 117,5400 1.234,00€ €",
                "30/09 10,5000 118,0000 1.239,00€ €",
                "Más detalle en el apartado");
    }

    @Test
    void integradoSoloSeReconoceConSuTitulo() {
        assertThat(integrado.soporta("x.pdf", pdfIntegrado())).isTrue();
        assertThat(integrado.soporta("x.pdf", pdfMovimientos())).isFalse();
    }

    @Test
    void integradoCombinaMesDelEncabezadoConElDiaYAnotaLaSubcuentaDeOrigen() {
        List<MovimientoImportado> movs = integrado.parsear(pdfIntegrado());

        assertThat(movs).hasSize(3);
        assertThat(movs.get(0)).isEqualTo(new MovimientoImportado(
                LocalDate.of(2026, 9, 5), new java.math.BigDecimal("-23.45"), "Compra Mercadona", "NÓMINA"));
        assertThat(movs.get(1).importe()).isEqualByComparingTo("1665.00");
        assertThat(movs.get(2).descripcionOriginal()).isEqualTo("Intereses a su favor");
        assertThat(movs.get(2).subcuenta()).isEqualTo("NARANJA");
        assertThat(movs.get(2).fecha()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void integradoNoLeeLasFilasDeLosFondos() {
        assertThat(integrado.parsear(pdfIntegrado())).noneMatch(m -> m.descripcionOriginal().contains("Fondo"));
    }

    @Test
    void integradoSinCabeceraDeMesNoDevuelveNada() {
        byte[] pdf = PdfSintetico.deLineas("Extracto Integrado", "Cuenta NÓMINA - IBAN: ES00",
                "05/09 05/09 Compra -1,00 € 9,00 €");

        assertThat(integrado.parsear(pdf)).isEmpty();
    }

    // ---------------------------------------------------------------- Extracto Integrado: fondos

    @Test
    void fondosSeReconocenSoloSiHayExtractoYSeccionDeFondos() {
        assertThat(fondos.soporta("x.pdf", pdfIntegrado())).isTrue();
        assertThat(fondos.soporta("x.pdf", PdfSintetico.deLineas("Extracto Integrado", "Cuentas"))).isFalse();
    }

    @Test
    void fondosUsaLaUltimaFilaComoPosicionActual() {
        List<PosicionImportada> pos = fondos.parsear(pdfIntegrado());

        assertThat(pos).hasSize(1);
        PosicionImportada p = pos.get(0);
        assertThat(p.isin()).isEqualTo("ING-1234567890.1");
        assertThat(p.nombre()).isEqualTo("Fondo Cartera Sostenible 5");
        assertThat(p.tipo()).isEqualTo(TipoActivo.FONDO);
        assertThat(p.cantidad()).isEqualByComparingTo("10.5");
        assertThat(p.precio()).isEqualByComparingTo("118.00");
    }
}
