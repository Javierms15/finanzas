package com.tfg.finanzas.web;

import com.tfg.finanzas.model.Categoria;
import com.tfg.finanzas.model.ReglaCategorizacion;
import com.tfg.finanzas.model.TipoCategoria;
import com.tfg.finanzas.model.TipoRendimiento;
import com.tfg.finanzas.repository.ReglaCategorizacionRepository;
import com.tfg.finanzas.support.ApiBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoriasApiTest extends ApiBase {

    @Autowired
    ReglaCategorizacionRepository reglaRepository;

    // ------------------------------------------------------------------ categorias y reglas

    @Test
    void crearYListarCategorias() throws Exception {
        Sesion s = registrar();

        enviar(s, "/categorias", """
                {"nombre":"Ocio","tipo":"GASTO","color":"#ff0000"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Ocio"))
                .andExpect(jsonPath("$.color").value("#ff0000"));
        enviar(s, "/categorias", """
                {"nombre":"Traspasos","tipo":"NEUTRO"}""").andExpect(status().isCreated());

        obtener(s, "/categorias").andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void crearUnaCategoriaValidaElNombreYElTipo() throws Exception {
        Sesion s = registrar();
        enviar(s, "/categorias", """
                {"nombre":"","tipo":"GASTO"}""").andExpect(status().isBadRequest());
        enviar(s, "/categorias", """
                {"nombre":"X","tipo":"OTRO"}""").andExpect(status().isBadRequest());
    }

    @Test
    void lasReglasSeListanPorPrioridadYSePuedenBorrar() throws Exception {
        Sesion s = registrar();
        long cat = crearCategoria(s, "Supermercado", "GASTO");
        enviar(s, "/reglas-categorizacion", """
                {"categoriaId":%d,"patron":"lidl","prioridad":5}""".formatted(cat)).andExpect(status().isCreated());
        long primera = idDe(enviar(s, "/reglas-categorizacion", """
                {"categoriaId":%d,"patron":"mercadona","prioridad":1}""".formatted(cat)));

        obtener(s, "/reglas-categorizacion")
                .andExpect(jsonPath("$[0].patron").value("mercadona"))
                .andExpect(jsonPath("$[1].patron").value("lidl"));

        borrar(s, "/reglas-categorizacion/" + primera).andExpect(status().isNoContent());
        obtener(s, "/reglas-categorizacion").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void crearOBorrarReglasConDatosInexistentesDevuelve400() throws Exception {
        Sesion s = registrar();
        enviar(s, "/reglas-categorizacion", """
                {"categoriaId":999999,"patron":"x","prioridad":1}""").andExpect(status().isBadRequest());
        enviar(s, "/reglas-categorizacion", """
                {"patron":"x","prioridad":1}""").andExpect(status().isBadRequest());
        borrar(s, "/reglas-categorizacion/999999").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ categorias sugeridas

    @Test
    void cargarLasSugeridasCreaCategoriasYReglasYEsIdempotente() throws Exception {
        Sesion s = registrar();

        String primera = enviar(s, "/categorias/sugeridas?usuarioId=" + s.usuarioId(), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriasCreadas").value(org.hamcrest.Matchers.greaterThan(10)))
                .andExpect(jsonPath("$.reglasCreadas").value(org.hamcrest.Matchers.greaterThan(50)))
                .andReturn().getResponse().getContentAsString();
        assertThat(primera).contains("categoriasCreadas");

        enviar(s, "/categorias/sugeridas?usuarioId=" + s.usuarioId(), "")
                .andExpect(jsonPath("$.categoriasCreadas").value(0))
                .andExpect(jsonPath("$.reglasCreadas").value(0));
    }

    @Test
    void lasCategoriasDeInteresesRetencionesYCashbackLlevanSuTipoDeRendimiento() throws Exception {
        Sesion s = registrar();
        enviar(s, "/categorias/sugeridas?usuarioId=" + s.usuarioId(), "");

        assertThat(categoriaRepository.findFirstByNombreIgnoreCase("Intereses").orElseThrow().getRendimiento())
                .isEqualTo(TipoRendimiento.INTERES);
        assertThat(categoriaRepository.findFirstByNombreIgnoreCase("Cashback").orElseThrow().getRendimiento())
                .isEqualTo(TipoRendimiento.CASHBACK);
        Categoria retenciones = categoriaRepository.findFirstByNombreIgnoreCase("Retenciones sobre intereses").orElseThrow();
        assertThat(retenciones.getRendimiento()).isEqualTo(TipoRendimiento.RETENCION);
        assertThat(retenciones.getTipo()).isEqualTo(TipoCategoria.GASTO);
        assertThat(categoriaRepository.findFirstByNombreIgnoreCase("Supermercado").orElseThrow().getRendimiento()).isNull();
    }

    @Test
    void lasTransferenciasAUnoMismoSeDetectanConElNombreDelUsuario() throws Exception {
        Sesion s = registrar("Ana Prueba Lopez", "ana-propia@correo.es");
        long cuenta = crearCuenta(s, "ING", "0");
        enviar(s, "/categorias/sugeridas?usuarioId=" + s.usuarioId(), "");

        subir(s, "/cuentas/" + cuenta + "/importar", "mov.csv",
                ("fecha,descripcion,importe\n2026-03-05,Transferencia emitida a Ana Prueba Lopez,-300.00\n"
                        + "2026-03-06,Transferencia emitida a Otra Persona,-20.00\n").getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk());

        obtener(s, "/cuentas/" + cuenta + "/movimientos")
                .andExpect(jsonPath("$[?(@.importe == -300)].categoria.nombre").value("Transferencias propias"))
                .andExpect(jsonPath("$[?(@.importe == -20)].categoria").value((Object) null));
    }

    @Test
    void laVersionAnteriorDeLasSugeridasSeMigraSinPerderMovimientos() throws Exception {
        Sesion s = registrar();
        Categoria antigua = categoriaRepository.save(Categoria.builder()
                .nombre("Intereses y bonificaciones").tipo(TipoCategoria.INGRESO).build());
        reglaRepository.save(ReglaCategorizacion.builder().categoria(antigua).patron("intereses a su favor").prioridad(101).build());
        reglaRepository.save(ReglaCategorizacion.builder().categoria(antigua).patron("saveback").prioridad(102).build());
        reglaRepository.save(ReglaCategorizacion.builder().categoria(antigua).patron("bonificacion").prioridad(103).build());

        enviar(s, "/categorias/sugeridas?usuarioId=" + s.usuarioId(), "").andExpect(status().isOk());

        Categoria migrada = categoriaRepository.findById(antigua.getId()).orElseThrow();
        assertThat(migrada.getNombre()).isEqualTo("Intereses");
        assertThat(migrada.getRendimiento()).isEqualTo(TipoRendimiento.INTERES);
        assertThat(reglaRepository.findAll().stream()
                .filter(r -> r.getCategoria().getId().equals(antigua.getId())).map(ReglaCategorizacion::getPatron))
                .doesNotContain("saveback", "bonificacion");
        assertThat(categoriaRepository.findFirstByNombreIgnoreCase("Intereses y bonificaciones")).isEmpty();
    }

    @Test
    void cargarLasSugeridasParaUnUsuarioInexistenteDevuelve400() throws Exception {
        Sesion s = registrar();
        enviar(s, "/categorias/sugeridas?usuarioId=999999", "").andExpect(status().isBadRequest());
    }
}
