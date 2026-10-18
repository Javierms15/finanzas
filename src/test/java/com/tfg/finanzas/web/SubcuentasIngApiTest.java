package com.tfg.finanzas.web;

import com.jayway.jsonpath.JsonPath;
import com.tfg.finanzas.support.ApiBase;
import com.tfg.finanzas.support.PdfSintetico;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El "Extracto Integrado" de ING junta la cuenta Nomina (corriente, sin intereses) y la Naranja (remunerada):
 * cada movimiento debe ir a su propia cuenta para que la tasa de interes solo se aplique al saldo de la Naranja.
 */
class SubcuentasIngApiTest extends ApiBase {

    Sesion s;
    long nomina;

    @BeforeEach
    void preparar() throws Exception {
        s = registrar();
        nomina = idDe(enviar(s, "/usuarios/" + s.usuarioId() + "/cuentas", """
                {"banco":"ING","tipo":"NOMINA","alias":"ING Nomina","moneda":"EUR","saldoInicial":545.81}"""));
        enviar(s, "/categorias/sugeridas?usuarioId=" + s.usuarioId(), "").andExpect(status().isOk());
    }

    private byte[] extracto() {
        return PdfSintetico.deLineas("Extracto Integrado", "Septiembre 2026 Saldo Final 6.201,25 €",
                "Cuenta NÓMINA - IBAN: ES00 0000 0000 0000 0000",
                "17/09 17/09 Pago GOOGLE ONE DUBLIN IE -2,99 € 100,00 €",
                "26/09 26/09 Ingreso Nómina 1.666,29 € 1.766,29 €",
                "Cuenta NARANJA - IBAN: ES11 1111 1111 1111 1111",
                "01/09 01/09 Intereses a su favor 4,67 € 5.529,65 €",
                "01/09 01/09 Impuestos retenidos a cuenta 19.0% -0,89 € 5.528,76 €");
    }

    private String url() {
        return "/usuarios/" + s.usuarioId() + "/cuentas";
    }

    @Test
    void cadaSubcuentaVaASuPropiaCuentaYLaNaranjaSeCreaComoRemunerada() throws Exception {
        subir(s, "/cuentas/" + nomina + "/importar", "integrado.pdf", extracto())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nuevos").value(4))
                .andExpect(jsonPath("$.cuentasCreadas.length()").value(1))
                .andExpect(jsonPath("$.cuentasCreadas[0]").value("ING Naranja"));

        obtener(s, url()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.alias=='ING Naranja')].tipo").value("AHORRO"))
                .andExpect(jsonPath("$[?(@.alias=='ING Naranja')].banco").value("ING"));

        // La Nomina conserva sus dos movimientos y la Naranja recibe los dos de intereses
        String saldos = obtener(s, url() + "/saldos").andReturn().getResponse().getContentAsString();
        assertThat(((Number) JsonPath.parse(saldos).read("$[?(@.alias=='ING Nomina')].saldoActual", java.util.List.class).get(0)).doubleValue())
                .isCloseTo(545.81 - 2.99 + 1666.29, org.assertj.core.data.Offset.offset(0.001));
        assertThat(((Number) JsonPath.parse(saldos).read("$[?(@.alias=='ING Naranja')].saldoActual", java.util.List.class).get(0)).doubleValue())
                .isCloseTo(3.78, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void lasDescripcionesYaNoLlevanElPrefijoDeLaCuenta() throws Exception {
        subir(s, "/cuentas/" + nomina + "/importar", "integrado.pdf", extracto());

        String cuerpo = obtener(s, "/cuentas/" + nomina + "/movimientos").andReturn().getResponse().getContentAsString();
        java.util.List<String> descripciones = JsonPath.read(cuerpo, "$[*].descripcionOriginal");
        assertThat(descripciones).containsExactlyInAnyOrder("Pago GOOGLE ONE DUBLIN IE", "Ingreso Nómina");
    }

    @Test
    void volverAImportarElMismoExtractoNoCreaNadaNuevoNiOtraCuenta() throws Exception {
        subir(s, "/cuentas/" + nomina + "/importar", "integrado.pdf", extracto());

        subir(s, "/cuentas/" + nomina + "/importar", "integrado.pdf", extracto())
                .andExpect(jsonPath("$.nuevos").value(0))
                .andExpect(jsonPath("$.duplicados").value(4))
                .andExpect(jsonPath("$.cuentasCreadas.length()").value(0));

        obtener(s, url()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void elInteresYLaRetencionDeLaNaranjaSiguenContandoComoRendimientoDeIng() throws Exception {
        subir(s, "/cuentas/" + nomina + "/importar", "integrado.pdf", extracto());

        obtener(s, "/usuarios/" + s.usuarioId() + "/patrimonio/rendimientos-mensuales")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].banco").value("ING"))
                .andExpect(jsonPath("$[0].intereses").value(4.67))
                .andExpect(jsonPath("$[0].retenciones").value(-0.89));
    }

    @Test
    void siYaExisteUnaCuentaConEseNombreSeUsaEnVezDeCrearOtra() throws Exception {
        long naranja = idDe(enviar(s, url(), """
                {"banco":"ING","tipo":"AHORRO","alias":"Mi cuenta Naranja","moneda":"EUR","tasaInteresAnual":0.0089}"""));

        subir(s, "/cuentas/" + nomina + "/importar", "integrado.pdf", extracto())
                .andExpect(jsonPath("$.cuentasCreadas.length()").value(0));

        obtener(s, "/cuentas/" + naranja + "/movimientos").andExpect(jsonPath("$.length()").value(2));
        obtener(s, url()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void importandoDesdeLaNaranjaLosMovimientosDeNominaVanALaNomina() throws Exception {
        long naranja = idDe(enviar(s, url(), """
                {"banco":"ING","tipo":"AHORRO","alias":"ING Naranja","moneda":"EUR"}"""));

        subir(s, "/cuentas/" + naranja + "/importar", "integrado.pdf", extracto())
                .andExpect(jsonPath("$.cuentasCreadas.length()").value(0));

        obtener(s, "/cuentas/" + nomina + "/movimientos").andExpect(jsonPath("$.length()").value(2));
        obtener(s, "/cuentas/" + naranja + "/movimientos").andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void siElBancoNoTieneCuentaNominaSeCreaSoloLaQueFalta() throws Exception {
        long soloAhorro = idDe(enviar(s, url(), """
                {"banco":"Otro banco","tipo":"AHORRO","alias":"Cuenta del otro banco","moneda":"EUR"}"""));

        subir(s, "/cuentas/" + soloAhorro + "/importar", "integrado.pdf", extracto())
                .andExpect(jsonPath("$.cuentasCreadas.length()").value(1))
                .andExpect(jsonPath("$.cuentasCreadas[0]").value("Otro banco Nómina"));

        obtener(s, "/cuentas/" + soloAhorro + "/movimientos").andExpect(jsonPath("$.length()").value(2));
        obtener(s, url()).andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void unFicheroSinSubcuentasVaEnteroALaCuentaElegida() throws Exception {
        subir(s, "/cuentas/" + nomina + "/importar", "mov.csv",
                "fecha,descripcion,importe\n2026-03-05,Compra,-5.00\n".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .andExpect(jsonPath("$.nuevos").value(1))
                .andExpect(jsonPath("$.cuentasCreadas.length()").value(0));

        obtener(s, url()).andExpect(jsonPath("$.length()").value(1));
    }
}
