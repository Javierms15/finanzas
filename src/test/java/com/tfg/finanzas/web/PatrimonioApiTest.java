package com.tfg.finanzas.web;

import com.tfg.finanzas.model.Categoria;
import com.tfg.finanzas.support.ApiBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Escenario: saldo inicial 1.000; enero 2025: +2.000 y -500 (supermercado); febrero 2025: +2.000, -700 (supermercado)
 * y un traspaso neutro de -1.000; y +50 en el mes en curso (que no entra en las medias por estar incompleto).
 */
class PatrimonioApiTest extends ApiBase {

    Sesion s;
    String base;
    long supermercado;

    @BeforeEach
    void preparar() throws Exception {
        s = registrar();
        base = "/usuarios/" + s.usuarioId() + "/patrimonio";
        long cuenta = crearCuenta(s, "ING", "1000");
        supermercado = crearCategoria(s, "Supermercado", "GASTO");
        long traspasos = crearCategoria(s, "Traspasos", "NEUTRO");
        Categoria super_ = categoriaRepository.findById(supermercado).orElseThrow();
        Categoria neutra = categoriaRepository.findById(traspasos).orElseThrow();

        movimiento(cuenta, LocalDate.of(2025, 1, 10), "2000", "Nomina", null);
        movimiento(cuenta, LocalDate.of(2025, 1, 15), "-500", "Compra", super_);
        movimiento(cuenta, LocalDate.of(2025, 2, 10), "2000", "Nomina", null);
        movimiento(cuenta, LocalDate.of(2025, 2, 15), "-700", "Compra", super_);
        movimiento(cuenta, LocalDate.of(2025, 2, 20), "-1000", "Traspaso a inversion", neutra);
        movimiento(cuenta, LocalDate.now(), "50", "Ingreso de este mes", null);
    }

    @Test
    void laAgregacionMensualExcluyeLoNeutroYAgrupaPorMes() throws Exception {
        obtener(s, base + "/agregacion-mensual")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].mes").value("2025-01"))
                .andExpect(jsonPath("$[0].totalIngresos").value(2000.0))
                .andExpect(jsonPath("$[0].totalGastos").value(-500.0))
                .andExpect(jsonPath("$[1].mes").value("2025-02"))
                .andExpect(jsonPath("$[1].totalGastos").value(-700.0))
                .andExpect(jsonPath("$[1].balance").value(1300.0));
    }

    @Test
    void laProyeccionUsaLasMediasDeLosMesesCerradosYElSaldoRealIncluidoLoNeutro() throws Exception {
        // saldo = 1000 + 2000 - 500 + 2000 - 700 - 1000 + 50 = 2850; ahorro mensual = 2000 - 600
        obtener(s, base + "/proyeccion?meses=3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patrimonioActual").value(2850.0))
                .andExpect(jsonPath("$.ingresoMedio").value(2000.0))
                .andExpect(jsonPath("$.gastoMedio").value(600.0))
                .andExpect(jsonPath("$.patrimonioProyectado").value(7050.0));
    }

    @Test
    void sinMovimientosLaProyeccionEsElSaldoInicial() throws Exception {
        Sesion otro = registrar();
        crearCuenta(otro, "ING", "300");

        obtener(otro, "/usuarios/" + otro.usuarioId() + "/patrimonio/proyeccion?meses=12")
                .andExpect(jsonPath("$.patrimonioActual").value(300.0))
                .andExpect(jsonPath("$.ingresoMedio").value(0))
                .andExpect(jsonPath("$.patrimonioProyectado").value(300.0));
    }

    @Test
    void simularUnAjusteDeUnaCategoriaRecalculaLaProyeccionSinTocarNada() throws Exception {
        obtener(s, base + "/proyeccion/simular?meses=3&categoriaId=" + supermercado + "&ajuste=100")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gastoMedioCategoriaActual").value(600.0))
                .andExpect(jsonPath("$.gastoMedioCategoriaSimulado").value(500.0))
                .andExpect(jsonPath("$.gastoMedioTotalSimulado").value(500.0))
                .andExpect(jsonPath("$.patrimonioProyectadoActual").value(7050.0))
                .andExpect(jsonPath("$.patrimonioProyectadoSimulado").value(7350.0));

        obtener(s, base + "/proyeccion?meses=3").andExpect(jsonPath("$.gastoMedio").value(600.0));
    }

    @Test
    void simularExigeCategoriaYAjuste() throws Exception {
        obtener(s, base + "/proyeccion/simular?meses=3").andExpect(status().isBadRequest());
    }

    @Test
    void losRendimientosMensualesSeparanInteresRetencionYCashbackPorBanco() throws Exception {
        Sesion r = registrar("Rendimientos", "rend-" + System.nanoTime() + "@correo.es");
        enviar(r, "/categorias/sugeridas?usuarioId=" + r.usuarioId(), "").andExpect(status().isOk());
        long ing = crearCuenta(r, "ING", "0");
        long tr = crearCuenta(r, "Trade Republic", "0");
        subir(r, "/cuentas/" + ing + "/importar", "ing.csv", ("fecha,descripcion,importe\n"
                + "2026-09-01,Intereses a su favor,4.67\n2026-09-01,Impuestos retenidos,-0.89\n").getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk());
        subir(r, "/cuentas/" + tr + "/importar", "tr.csv", ("fecha,descripcion,importe\n"
                + "2026-09-01,Interest payment,16.65\n2026-09-01,Saveback bonus,8.44\n2026-10-01,Interest payment,10.00\n")
                .getBytes(StandardCharsets.UTF_8)).andExpect(status().isOk());

        obtener(r, "/usuarios/" + r.usuarioId() + "/patrimonio/rendimientos-mensuales")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.mes=='2026-09' && @.banco=='ING')].intereses").value(4.67))
                .andExpect(jsonPath("$[?(@.mes=='2026-09' && @.banco=='ING')].retenciones").value(-0.89))
                .andExpect(jsonPath("$[?(@.mes=='2026-09' && @.banco=='Trade Republic')].intereses").value(16.65))
                .andExpect(jsonPath("$[?(@.mes=='2026-09' && @.banco=='Trade Republic')].cashback").value(8.44))
                .andExpect(jsonPath("$[?(@.mes=='2026-10')].intereses").value(10.0));
    }
}
