package com.tfg.finanzas.web;

import com.tfg.finanzas.support.ApiBase;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PrevistosApiTest extends ApiBase {

    private String url(Sesion s) {
        return "/usuarios/" + s.usuarioId() + "/previstos";
    }

    @Test
    void crearUnPrevistoLoDejaPendienteYRecortaLaDescripcion() throws Exception {
        Sesion s = registrar();

        enviar(s, url(s), """
                {"fecha":"2026-12-01","importe":-450.50,"descripcion":"  Seguro del coche  "}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descripcion").value("Seguro del coche"))
                .andExpect(jsonPath("$.importe").value(-450.50))
                .andExpect(jsonPath("$.cumplido").value(false));
    }

    @Test
    void elListadoVieneOrdenadoPorFecha() throws Exception {
        Sesion s = registrar();
        enviar(s, url(s), """
                {"fecha":"2026-12-01","importe":-10,"descripcion":"Segundo"}""");
        enviar(s, url(s), """
                {"fecha":"2026-11-01","importe":20,"descripcion":"Primero"}""");

        obtener(s, url(s))
                .andExpect(jsonPath("$[0].descripcion").value("Primero"))
                .andExpect(jsonPath("$[1].descripcion").value("Segundo"));
    }

    @Test
    void rechazaImporteCeroYCamposVacios() throws Exception {
        Sesion s = registrar();
        enviar(s, url(s), """
                {"fecha":"2026-12-01","importe":0,"descripcion":"Nada"}""").andExpect(status().isBadRequest());
        enviar(s, url(s), """
                {"fecha":"2026-12-01","importe":5,"descripcion":""}""").andExpect(status().isBadRequest());
        enviar(s, url(s), """
                {"importe":5,"descripcion":"Sin fecha"}""").andExpect(status().isBadRequest());
    }

    @Test
    void sePuedeMarcarComoCumplidoYEliminar() throws Exception {
        Sesion s = registrar();
        long id = idDe(enviar(s, url(s), """
                {"fecha":"2026-12-01","importe":-10,"descripcion":"Algo"}"""));

        modificar(s, url(s) + "/" + id, """
                {"cumplido":true}""").andExpect(status().isOk()).andExpect(jsonPath("$.cumplido").value(true));
        modificar(s, url(s) + "/" + id, "{}").andExpect(status().isBadRequest());

        borrar(s, url(s) + "/" + id).andExpect(status().isNoContent());
        obtener(s, url(s)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void noSePuedeTocarUnPrevistoAjenoNiInexistente() throws Exception {
        Sesion ana = registrar();
        Sesion luis = registrar();
        long deAna = idDe(enviar(ana, url(ana), """
                {"fecha":"2026-12-01","importe":-10,"descripcion":"De Ana"}"""));

        modificar(luis, url(luis) + "/" + deAna, "{\"cumplido\":true}").andExpect(status().isBadRequest());
        borrar(luis, url(luis) + "/" + deAna).andExpect(status().isBadRequest());
        borrar(luis, url(luis) + "/999999").andExpect(status().isBadRequest());
    }
}
