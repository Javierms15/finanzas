package com.tfg.finanzas.web;

import com.tfg.finanzas.support.ApiBase;
import com.tfg.finanzas.support.PdfSintetico;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InversionApiTest extends ApiBase {

    Sesion s;
    long activo;
    String base;

    @BeforeEach
    void preparar() throws Exception {
        s = registrar();
        base = "/usuarios/" + s.usuarioId() + "/inversion";
        activo = idDe(enviar(s, "/activos", """
                {"ticker":"VWCE","nombre":"Vanguard FTSE All-World","tipo":"ETF"}""").andExpect(status().isCreated()));
    }

    private String movimiento(String tipo, String fecha, String cantidad, String precio) {
        return """
                {"activoId":%d,"tipo":"%s","fecha":"%s","cantidad":%s,"precio":%s}""".formatted(activo, tipo, fecha, cantidad, precio);
    }

    // ------------------------------------------------------------------ activos

    @Test
    void crearYListarActivos() throws Exception {
        enviar(s, "/activos", """
                {"ticker":"AAPL","nombre":"Apple","tipo":"ACCION"}""")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("ACCION"));

        obtener(s, "/activos").andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void crearUnActivoValidaLosCampos() throws Exception {
        enviar(s, "/activos", """
                {"ticker":"","nombre":"X","tipo":"ETF"}""").andExpect(status().isBadRequest());
        enviar(s, "/activos", """
                {"ticker":"X","nombre":"X","tipo":"INVENTADO"}""").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ compras y ventas

    @Test
    void lasComprasCalculanElPrecioMedioPonderado() throws Exception {
        enviar(s, base + "/movimientos", movimiento("COMPRA", "2026-01-10", "10", "100"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cantidad").value(10))
                .andExpect(jsonPath("$.precioMedioCompra").value(100));

        enviar(s, base + "/movimientos", movimiento("COMPRA", "2026-02-10", "10", "120"))
                .andExpect(jsonPath("$.cantidad").value(20))
                .andExpect(jsonPath("$.precioMedioCompra").value(110));
    }

    @Test
    void unaVentaBajaLaCantidadPeroNoElPrecioMedio() throws Exception {
        enviar(s, base + "/movimientos", movimiento("COMPRA", "2026-01-10", "20", "110"));

        enviar(s, base + "/movimientos", movimiento("VENTA", "2026-03-10", "5", "150"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cantidad").value(15))
                .andExpect(jsonPath("$.precioMedioCompra").value(110));
    }

    @Test
    void venderTodoDejaElPrecioMedioACero() throws Exception {
        enviar(s, base + "/movimientos", movimiento("COMPRA", "2026-01-10", "5", "100"));

        enviar(s, base + "/movimientos", movimiento("VENTA", "2026-03-10", "5", "100"))
                .andExpect(jsonPath("$.cantidad").value(0))
                .andExpect(jsonPath("$.precioMedioCompra").value(0));
    }

    @Test
    void venderMasDeLoQueSeTieneDevuelve400ConElMotivo() throws Exception {
        enviar(s, base + "/movimientos", movimiento("COMPRA", "2026-01-10", "5", "100"));

        enviar(s, base + "/movimientos", movimiento("VENTA", "2026-03-10", "6", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("No se pueden vender")));
    }

    @Test
    void unDividendoNoCambiaLaCantidadNiElPrecioMedio() throws Exception {
        enviar(s, base + "/movimientos", movimiento("COMPRA", "2026-01-10", "5", "100"));

        enviar(s, base + "/movimientos", movimiento("DIVIDENDO", "2026-03-10", "5", "2"))
                .andExpect(jsonPath("$.cantidad").value(5))
                .andExpect(jsonPath("$.precioMedioCompra").value(100));
    }

    @Test
    void registrarUnMovimientoConDatosInvalidosDevuelve400() throws Exception {
        enviar(s, base + "/movimientos", """
                {"activoId":%d,"tipo":"COMPRA"}""".formatted(activo)).andExpect(status().isBadRequest());
        enviar(s, base + "/movimientos", """
                {"activoId":999999,"tipo":"COMPRA","fecha":"2026-01-10","cantidad":1,"precio":1}""")
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ valoracion y proyeccion

    @Test
    void sinCotizacionLaPosicionSeValoraAlPrecioMedioDeCompra() throws Exception {
        enviar(s, base + "/movimientos", movimiento("COMPRA", "2026-01-10", "10", "100"));
        when(precios.obtenerPrecioActual("VWCE")).thenReturn(Optional.empty());

        obtener(s, base + "/posiciones")
                .andExpect(jsonPath("$[0].ticker").value("VWCE"))
                .andExpect(jsonPath("$[0].tipo").value("ETF"))
                .andExpect(jsonPath("$[0].valorActual").value(1000))
                .andExpect(jsonPath("$[0].rendimiento").value(0));
    }

    @Test
    void conCotizacionElValorYElRendimientoUsanElPrecioDeMercado() throws Exception {
        enviar(s, base + "/movimientos", movimiento("COMPRA", "2026-01-10", "15", "110"));
        when(precios.obtenerPrecioActual("VWCE")).thenReturn(Optional.of(new BigDecimal("130")));

        obtener(s, base + "/posiciones")
                .andExpect(jsonPath("$[0].precioActual").value(130))
                .andExpect(jsonPath("$[0].valorActual").value(1950))
                .andExpect(jsonPath("$[0].rendimiento").value(300));
    }

    @Test
    void laProyeccionComponeElRetornoYSumaLasAportaciones() throws Exception {
        enviar(s, base + "/movimientos", movimiento("COMPRA", "2026-01-10", "10", "100"));

        obtener(s, base + "/proyeccion?anios=2&retornoEsperado=0.10&aportacionAnual=100")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorActual").value(1000))
                .andExpect(jsonPath("$.valorProyectado").value(1410));
    }

    @Test
    void laProyeccionExigeElRetornoEsperado() throws Exception {
        obtener(s, base + "/proyeccion?anios=2").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ importar posiciones

    private byte[] extractoValores(String precioEtf) {
        return PdfSintetico.deLineas(
                "TRADE REPUBLIC", "EXTRACTO DE LA CUENTA DE VALORES",
                "10 tít. ISHARES CORE MSCI WORLD UCITS ETF", "ISIN: IE00B4L5Y983", "Cuenta de valores en Alemania", precioEtf,
                "2,5 tít. APPLE INC.", "ISIN: US0378331005", "200,00",
                "NÚMERO DE POSICIONES: 2");
    }

    @Test
    void importarUnExtractoDeValoresCreaLosActivosYLasPosiciones() throws Exception {
        subir(s, base + "/posiciones/importar", "valores.pdf", extractoValores("98,45"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEncontradas").value(2))
                .andExpect(jsonPath("$.actualizadas").value(2));

        obtener(s, base + "/posiciones")
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.ticker=='IE00B4L5Y983')].tipo").value("ETF"))
                .andExpect(jsonPath("$[?(@.ticker=='IE00B4L5Y983')].cantidad").value(10))
                .andExpect(jsonPath("$[?(@.ticker=='US0378331005')].tipo").value("ACCION"));
    }

    @Test
    void volverAImportarActualizaLasPosicionesEnVezDeDuplicarlas() throws Exception {
        subir(s, base + "/posiciones/importar", "valores.pdf", extractoValores("98,45"));
        subir(s, base + "/posiciones/importar", "valores.pdf", extractoValores("101,00"))
                .andExpect(status().isOk());

        obtener(s, base + "/posiciones")
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.ticker=='IE00B4L5Y983')].precioMedioCompra").value(101.0));
    }

    @Test
    void importarLosFondosDelExtractoIntegradoDeIng() throws Exception {
        byte[] pdf = PdfSintetico.deLineas("Extracto Integrado", "• Fondos de Inversión",
                "Fondo Cartera Sostenible 5 - 1234567890.1 Saldo final 1.234,56 €",
                "30/09 10,5000 118,0000 1.239,00€ €", "Más detalle en el apartado");

        subir(s, base + "/posiciones/importar", "integrado.pdf", pdf)
                .andExpect(jsonPath("$.totalEncontradas").value(1));

        obtener(s, base + "/posiciones")
                .andExpect(jsonPath("$[0].tipo").value("FONDO"))
                .andExpect(jsonPath("$[0].cantidad").value(10.5))
                .andExpect(jsonPath("$[0].valorActual").value(1239.0));
    }

    @Test
    void importarUnFormatoDesconocidoDevuelve400() throws Exception {
        subir(s, base + "/posiciones/importar", "cosa.pdf", PdfSintetico.deLineas("Otro documento"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Formato de fichero no reconocido")));
    }
}
