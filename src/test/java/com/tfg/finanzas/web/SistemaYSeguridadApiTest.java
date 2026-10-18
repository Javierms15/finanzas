package com.tfg.finanzas.web;

import com.tfg.finanzas.support.ApiBase;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SistemaYSeguridadApiTest extends ApiBase {

    // ------------------------------------------------------------------ latido del lanzador (publico)

    @Test
    void loEndpointsDelSistemaNoRequierenSesion() throws Exception {
        mvc.perform(post("/sistema/latido")).andExpect(status().isNoContent());
        mvc.perform(post("/sistema/cierre")).andExpect(status().isNoContent());
        mvc.perform(get("/sistema/actividad")).andExpect(status().isOk());
    }

    @Test
    void unCierreSinLatidoPosteriorQuedaPendienteYUnLatidoLoAnula() throws Exception {
        mvc.perform(post("/sistema/latido")).andExpect(status().isNoContent());
        Thread.sleep(5);
        mvc.perform(post("/sistema/cierre")).andExpect(status().isNoContent());
        mvc.perform(get("/sistema/actividad"))
                .andExpect(jsonPath("$.cierrePendiente").value(true))
                .andExpect(jsonPath("$.latidos").isNumber());

        Thread.sleep(5);
        mvc.perform(post("/sistema/latido"));
        mvc.perform(get("/sistema/actividad")).andExpect(jsonPath("$.cierrePendiente").value(false));
    }

    // ------------------------------------------------------------------ CORS

    @Test
    void elFrontendDeDesarrolloPuedeLlamarConPatchYDelete() throws Exception {
        for (String metodo : new String[]{"PATCH", "DELETE", "PUT"}) {
            mvc.perform(options("/usuarios/1/cuentas/1/saldo-inicial")
                            .header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", metodo)
                            .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                    .andExpect(header().string("Access-Control-Allow-Methods", containsString(metodo)));
        }
    }

    @Test
    void otroOrigenNoPasaElControlDeCors() throws Exception {
        mvc.perform(options("/usuarios/1/cuentas")
                        .header("Origin", "http://sitio-malicioso.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ superficie publica

    @Test
    void soloLasRutasDeAutenticacionYSistemaSonPublicas() throws Exception {
        mvc.perform(get("/activos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/reglas-categorizacion")).andExpect(status().isUnauthorized());
        mvc.perform(get("/usuarios/1/patrimonio/snapshots")).andExpect(status().isUnauthorized());
        mvc.perform(post("/categorias/recategorizar")).andExpect(status().isUnauthorized());
    }

    @Test
    void lasFotosDelPatrimonioSoloSePuedenLeerNoCrearDesdeLaApi() throws Exception {
        Sesion s = registrar();
        enviar(s, "/usuarios/" + s.usuarioId() + "/patrimonio/snapshots", "{}").andExpect(status().isMethodNotAllowed());
        borrar(s, "/usuarios/" + s.usuarioId() + "/patrimonio/snapshots/1").andExpect(status().is4xxClientError());
    }
}
