package com.tfg.finanzas.web;

import com.tfg.finanzas.support.ApiBase;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CuentasApiTest extends ApiBase {

    private String cuentaJson(String extra) {
        return """
                {"banco":"ING","tipo":"NOMINA","alias":"Cuenta principal","moneda":"EUR"%s}""".formatted(extra);
    }

    @Test
    void crearUnaCuentaDevuelve201YElSaldoInicialPorDefectoEsCero() throws Exception {
        Sesion s = registrar();

        enviar(s, "/usuarios/" + s.usuarioId() + "/cuentas", cuentaJson(""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.banco").value("ING"))
                .andExpect(jsonPath("$.saldoInicial").value(0))
                .andExpect(jsonPath("$.tasaInteresAnual").doesNotExist());
    }

    @Test
    void crearUnaCuentaConSaldoYTasaDeInteres() throws Exception {
        Sesion s = registrar();

        enviar(s, "/usuarios/" + s.usuarioId() + "/cuentas", cuentaJson(",\"saldoInicial\":1250.50,\"tasaInteresAnual\":0.0378"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldoInicial").value(1250.50))
                .andExpect(jsonPath("$.tasaInteresAnual").value(0.0378));
    }

    @Test
    void crearUnaCuentaValidaLosCamposObligatorios() throws Exception {
        Sesion s = registrar();

        enviar(s, "/usuarios/" + s.usuarioId() + "/cuentas", """
                {"banco":"","tipo":"NOMINA","alias":"x","moneda":"EUR"}""").andExpect(status().isBadRequest());
        enviar(s, "/usuarios/" + s.usuarioId() + "/cuentas", """
                {"banco":"ING","alias":"x","moneda":"EUR"}""").andExpect(status().isBadRequest());
        enviar(s, "/usuarios/" + s.usuarioId() + "/cuentas", """
                {"banco":"ING","tipo":"INVENTADO","alias":"x","moneda":"EUR"}""").andExpect(status().isBadRequest());
    }

    @Test
    void crearUnaCuentaParaUnUsuarioInexistenteDevuelve400() throws Exception {
        Sesion s = registrar();
        enviar(s, "/usuarios/999999/cuentas", cuentaJson("")).andExpect(status().isBadRequest());
    }

    @Test
    void listarDevuelveLasCuentasDelUsuario() throws Exception {
        Sesion s = registrar();
        crearCuenta(s, "ING", "0");
        crearCuenta(s, "Trade Republic", "0");

        obtener(s, "/usuarios/" + s.usuarioId() + "/cuentas")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void losSaldosActualesSumanElSaldoInicialMasLosMovimientos() throws Exception {
        Sesion s = registrar();
        long cuenta = crearCuenta(s, "ING", "1000");
        movimiento(cuenta, LocalDate.of(2026, 1, 5), "250.25", "Ingreso", null);
        movimiento(cuenta, LocalDate.of(2026, 1, 6), "-100.00", "Gasto", null);

        obtener(s, "/usuarios/" + s.usuarioId() + "/cuentas/saldos")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].saldoInicial").value(1000))
                .andExpect(jsonPath("$[0].saldoActual").value(1150.25))
                .andExpect(jsonPath("$[0].ultimoMovimiento").value("2026-01-06"));
    }

    @Test
    void unaCuentaSinMovimientosNoTieneFechaDeUltimoMovimiento() throws Exception {
        Sesion s = registrar();
        crearCuenta(s, "ING", "300");

        obtener(s, "/usuarios/" + s.usuarioId() + "/cuentas/saldos")
                .andExpect(jsonPath("$[0].saldoActual").value(300))
                .andExpect(jsonPath("$[0].ultimoMovimiento").doesNotExist());
    }

    @Test
    void sePuedeCambiarElSaldoInicial() throws Exception {
        Sesion s = registrar();
        long cuenta = crearCuenta(s, "ING", "0");
        String base = "/usuarios/" + s.usuarioId() + "/cuentas/" + cuenta;

        modificar(s, base + "/saldo-inicial", """
                {"saldoInicial":777.77}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoInicial").value(777.77));

        modificar(s, base + "/saldo-inicial", "{}").andExpect(status().isBadRequest());
    }

    @Test
    void laTasaDeInteresConservaSusDecimalesYSePuedeQuitar() throws Exception {
        Sesion s = registrar();
        long cuenta = crearCuenta(s, "ING", "0");
        String url = "/usuarios/" + s.usuarioId() + "/cuentas/" + cuenta + "/tasa-interes";

        // Regresion: Hibernate creaba la columna con 2 decimales y 0,0378 se guardaba como 0,04
        modificar(s, url, """
                {"tasaInteresAnual":0.0378}""").andExpect(status().isOk());
        obtener(s, "/usuarios/" + s.usuarioId() + "/cuentas")
                .andExpect(jsonPath("$[0].tasaInteresAnual").value(0.0378));

        modificar(s, url, """
                {"tasaInteresAnual":null}""").andExpect(status().isOk());
        obtener(s, "/usuarios/" + s.usuarioId() + "/cuentas")
                .andExpect(jsonPath("$[0].tasaInteresAnual").doesNotExist());
    }

    @Test
    void noSePuedeModificarUnaCuentaQueNoEsDelUsuarioIndicadoNiUnaInexistente() throws Exception {
        Sesion ana = registrar();
        Sesion luis = registrar();
        long cuentaDeAna = crearCuenta(ana, "ING", "0");

        modificar(luis, "/usuarios/" + luis.usuarioId() + "/cuentas/" + cuentaDeAna + "/saldo-inicial", """
                {"saldoInicial":1}""").andExpect(status().isBadRequest());
        modificar(luis, "/usuarios/" + luis.usuarioId() + "/cuentas/999999/tasa-interes", """
                {"tasaInteresAnual":0.01}""").andExpect(status().isBadRequest());
    }
}
