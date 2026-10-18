package com.tfg.finanzas.web;

import com.jayway.jsonpath.JsonPath;
import com.tfg.finanzas.support.ApiBase;
import com.tfg.finanzas.support.PdfSintetico;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImportacionYMovimientosApiTest extends ApiBase {

    Sesion s;
    long cuenta;
    long supermercado;

    @BeforeEach
    void preparar() throws Exception {
        s = registrar();
        cuenta = crearCuenta(s, "ING", "0");
        supermercado = crearCategoria(s, "Supermercado", "GASTO");
        enviar(s, "/reglas-categorizacion", """
                {"categoriaId":%d,"patron":"mercadona","prioridad":1}""".formatted(supermercado)).andExpect(status().isCreated());
    }

    private static byte[] csv(String... filas) {
        return ("fecha,descripcion,importe\n" + String.join("\n", filas) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private String importar(String nombre, byte[] contenido) throws Exception {
        return subir(s, "/cuentas/" + cuenta + "/importar", nombre, contenido)
                .andReturn().getResponse().getContentAsString();
    }

    // ------------------------------------------------------------------ importacion

    @Test
    void importarUnCsvCreaLosMovimientosYLosCategorizaPorReglas() throws Exception {
        subir(s, "/cuentas/" + cuenta + "/importar", "mov.csv",
                csv("2026-03-05,Compra Mercadona,-23.45", "2026-03-06,Ingreso extra,100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEncontrados").value(2))
                .andExpect(jsonPath("$.nuevos").value(2))
                .andExpect(jsonPath("$.duplicados").value(0))
                .andExpect(jsonPath("$.duplicadosProbables").value(0));

        obtener(s, "/cuentas/" + cuenta + "/movimientos")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.descripcionOriginal=='Compra Mercadona')].categoria.nombre").value("Supermercado"))
                .andExpect(jsonPath("$[?(@.descripcionOriginal=='Compra Mercadona')].origen").value("CSV"))
                .andExpect(jsonPath("$[?(@.descripcionOriginal=='Ingreso extra')].categoria").value((Object) null));
    }

    @Test
    void volverAImportarElMismoFicheroNoDuplicaNada() throws Exception {
        byte[] fichero = csv("2026-03-05,Compra Mercadona,-23.45", "2026-03-06,Ingreso extra,100.00");
        importar("mov.csv", fichero);

        subir(s, "/cuentas/" + cuenta + "/importar", "mov.csv", fichero)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nuevos").value(0))
                .andExpect(jsonPath("$.duplicados").value(2));

        obtener(s, "/cuentas/" + cuenta + "/movimientos").andExpect(jsonPath("$.length()").value(1 + 1));
    }

    @Test
    void elMismoMovimientoDescritoDeOtraFormaSeDetectaComoDuplicadoProbable() throws Exception {
        importar("a.csv", csv("2026-03-05,Compra Mercadona,-23.45"));

        subir(s, "/cuentas/" + cuenta + "/importar", "b.csv", csv("2026-03-05,COMPRA EN MERCADONA SA,-23.45"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nuevos").value(0))
                .andExpect(jsonPath("$.duplicados").value(0))
                .andExpect(jsonPath("$.duplicadosProbables").value(1));
    }

    @Test
    void elMismoImporteYDescripcionEnOtraCuentaNoEsDuplicado() throws Exception {
        long otraCuenta = crearCuenta(s, "Trade Republic", "0");
        importar("a.csv", csv("2026-03-05,Compra Mercadona,-23.45"));

        subir(s, "/cuentas/" + otraCuenta + "/importar", "a.csv", csv("2026-03-05,Compra Mercadona,-23.45"))
                .andExpect(jsonPath("$.nuevos").value(1));
    }

    @Test
    void unaDescripcionMuyLargaSeGuardaSinTruncar() throws Exception {
        String larga = "Transferencia " + "x".repeat(600);
        importar("larga.csv", csv("2026-03-05," + larga + ",-10.00"));

        String cuerpo = obtener(s, "/cuentas/" + cuenta + "/movimientos").andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(cuerpo, "$[0].descripcionOriginal")).isEqualTo(larga);
    }

    @Test
    void unFormatoDesconocidoDevuelve400ConElMotivo() throws Exception {
        subir(s, "/cuentas/" + cuenta + "/importar", "notas.txt", "hola".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Formato de fichero no reconocido: notas.txt")));
    }

    @Test
    void importarEnUnaCuentaInexistenteDevuelve400() throws Exception {
        subir(s, "/cuentas/999999/importar", "mov.csv", csv("2026-03-05,X,-1.00"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Cuenta no encontrada")));
    }

    @Test
    void unCsvConCabeceraDesconocidaDevuelve400() throws Exception {
        subir(s, "/cuentas/" + cuenta + "/importar", "raro.csv", "a,b,c\n1,2,3\n".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("No se han reconocido las columnas")));
    }

    @Test
    void importarUnPdfDeIngMarcaElOrigenComoPdf() throws Exception {
        byte[] pdf = PdfSintetico.deLineas(
                "Movimientos de la Cuenta", "F. VALOR CATEGORÍA DESCRIPCIÓN IMPORTE SALDO",
                "05/09/2026 Compra Mercadona -23.45 1,476.55", "06/09/2026 Nomina 1,665.00 3,141.55");

        subir(s, "/cuentas/" + cuenta + "/importar", "ing.pdf", pdf)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nuevos").value(2));

        obtener(s, "/cuentas/" + cuenta + "/movimientos")
                .andExpect(jsonPath("$[0].origen").value("PDF"));
    }

    @Test
    void elMismoMovimientoEnLosDosFormatosDeIngSeDetectaComoDuplicado() throws Exception {
        importar("ing.pdf", PdfSintetico.deLineas(
                "Movimientos de la Cuenta", "F. VALOR CATEGORÍA DESCRIPCIÓN IMPORTE SALDO",
                "05/09/2026 Compra Mercadona -23.45 1,476.55"));

        byte[] integrado = PdfSintetico.deLineas("Extracto Integrado", "Septiembre 2026 Saldo Final 3.000,00 €",
                "Cuenta NÓMINA - IBAN: ES00 0000", "05/09 05/09 Compra Mercadona -23,45 € 1.476,55 €");

        subir(s, "/cuentas/" + cuenta + "/importar", "integrado.pdf", integrado)
                .andExpect(jsonPath("$.nuevos").value(0))
                .andExpect(jsonPath("$.duplicados").value(1));
    }

    @Test
    void elMismoMovimientoConOtraDescripcionEnElOtroFormatoDeIngEsDuplicadoProbable() throws Exception {
        importar("ing.pdf", PdfSintetico.deLineas(
                "Movimientos de la Cuenta", "F. VALOR CATEGORÍA DESCRIPCIÓN IMPORTE SALDO",
                "05/09/2026 Transferencia emitida a BlaBlaCar -12.00 1,476.55"));

        byte[] integrado = PdfSintetico.deLineas("Extracto Integrado", "Septiembre 2026 Saldo Final 3.000,00 €",
                "Cuenta NÓMINA - IBAN: ES00 0000", "05/09 05/09 Transferencia emitida -12,00 € 1.476,55 €");

        subir(s, "/cuentas/" + cuenta + "/importar", "integrado.pdf", integrado)
                .andExpect(jsonPath("$.nuevos").value(0))
                .andExpect(jsonPath("$.duplicadosProbables").value(1));
    }

    // ------------------------------------------------------------------ categorias de movimientos

    @Test
    void cambiarLaCategoriaAManoLaMarcaComoManualYLaRecategorizacionNoLaToca() throws Exception {
        importar("mov.csv", csv("2026-03-05,Compra Mercadona,-23.45"));
        long ocio = crearCategoria(s, "Ocio", "GASTO");
        long movimiento = ((Number) JsonPath.read(
                obtener(s, "/cuentas/" + cuenta + "/movimientos").andReturn().getResponse().getContentAsString(), "$[0].id")).longValue();

        modificar(s, "/cuentas/" + cuenta + "/movimientos/" + movimiento + "/categoria", """
                {"categoriaId":%d}""".formatted(ocio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria.nombre").value("Ocio"))
                .andExpect(jsonPath("$.categoriaManual").value(true));

        enviar(s, "/categorias/recategorizar?todas=true", "").andExpect(status().isOk()).andExpect(content().string("0"));
        obtener(s, "/cuentas/" + cuenta + "/movimientos").andExpect(jsonPath("$[0].categoria.nombre").value("Ocio"));
    }

    @Test
    void quitarLaCategoriaManualVuelveALaQueDanLasReglas() throws Exception {
        importar("mov.csv", csv("2026-03-05,Compra Mercadona,-23.45"));
        long ocio = crearCategoria(s, "Ocio", "GASTO");
        long movimiento = ((Number) JsonPath.read(
                obtener(s, "/cuentas/" + cuenta + "/movimientos").andReturn().getResponse().getContentAsString(), "$[0].id")).longValue();
        String url = "/cuentas/" + cuenta + "/movimientos/" + movimiento + "/categoria";
        modificar(s, url, "{\"categoriaId\":" + ocio + "}");

        modificar(s, url, "{\"categoriaId\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria.nombre").value("Supermercado"))
                .andExpect(jsonPath("$.categoriaManual").value(false));
    }

    @Test
    void cambiarLaCategoriaDeUnMovimientoDeOtraCuentaODeUnaCategoriaInexistenteDevuelve400() throws Exception {
        importar("mov.csv", csv("2026-03-05,Compra Mercadona,-23.45"));
        long otraCuenta = crearCuenta(s, "Otro", "0");
        long movimiento = ((Number) JsonPath.read(
                obtener(s, "/cuentas/" + cuenta + "/movimientos").andReturn().getResponse().getContentAsString(), "$[0].id")).longValue();

        modificar(s, "/cuentas/" + otraCuenta + "/movimientos/" + movimiento + "/categoria", "{\"categoriaId\":null}")
                .andExpect(status().isBadRequest());
        modificar(s, "/cuentas/" + cuenta + "/movimientos/" + movimiento + "/categoria", "{\"categoriaId\":999999}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void recategorizarPendientesAplicaLasReglasNuevasSoloALosQueNoTienenCategoria() throws Exception {
        importar("mov.csv", csv("2026-03-05,Compra Lidl,-10.00", "2026-03-06,Compra Mercadona,-5.00"));
        enviar(s, "/reglas-categorizacion", """
                {"categoriaId":%d,"patron":"lidl","prioridad":2}""".formatted(supermercado));

        enviar(s, "/categorias/recategorizar", "").andExpect(status().isOk()).andExpect(content().string("1"));
        obtener(s, "/cuentas/" + cuenta + "/movimientos")
                .andExpect(jsonPath("$[?(@.categoria == null)]").isEmpty());
    }

    // ------------------------------------------------------------------ agregacion mensual

    @Test
    void laAgregacionMensualSeparaIngresosYGastosYExcluyeLasCategoriasNeutras() throws Exception {
        long traspaso = crearCategoria(s, "Traspasos", "NEUTRO");
        enviar(s, "/reglas-categorizacion", """
                {"categoriaId":%d,"patron":"traspaso","prioridad":3}""".formatted(traspaso));
        importar("mov.csv", csv("2026-03-05,Compra Mercadona,-23.45", "2026-03-06,Ingreso extra,100.00",
                "2026-03-07,Traspaso a inversion,-500.00", "2026-04-01,Compra Mercadona,-10.00"));

        obtener(s, "/cuentas/" + cuenta + "/movimientos/agregacion-mensual")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].mes").value("2026-03"))
                .andExpect(jsonPath("$[0].totalIngresos").value(100.0))
                .andExpect(jsonPath("$[0].totalGastos").value(-23.45))
                .andExpect(jsonPath("$[0].balance").value(76.55))
                .andExpect(jsonPath("$[1].mes").value("2026-04"));
    }
}
