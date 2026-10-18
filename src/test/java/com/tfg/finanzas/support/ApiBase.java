package com.tfg.finanzas.support;

import com.jayway.jsonpath.JsonPath;
import com.tfg.finanzas.model.Categoria;
import com.tfg.finanzas.model.CuentaBancaria;
import com.tfg.finanzas.model.Movimiento;
import com.tfg.finanzas.model.OrigenMovimiento;
import com.tfg.finanzas.repository.CategoriaRepository;
import com.tfg.finanzas.repository.CuentaBancariaRepository;
import com.tfg.finanzas.repository.MovimientoRepository;
import com.tfg.finanzas.service.PrecioMercadoProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Base de los tests que llaman a la API REST de verdad (filtros de seguridad y JWT incluidos) sobre PostgreSQL.
 * El proveedor de precios de mercado se sustituye por un mock: los tests no deben depender de internet.
 */
@AutoConfigureMockMvc
public abstract class ApiBase extends IntegracionBase {

    /** Una sesion ya iniciada: el token y el id del usuario. */
    public record Sesion(String token, long usuarioId, String email) {
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected CuentaBancariaRepository cuentaRepository;
    @Autowired
    protected MovimientoRepository movimientoRepository;
    @Autowired
    protected CategoriaRepository categoriaRepository;

    @MockitoBean
    protected PrecioMercadoProvider precios;

    // ------------------------------------------------------------------ sesiones

    protected Sesion registrar(String nombre, String email) throws Exception {
        String respuesta = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"%s","email":"%s","password":"contrasena-segura-1"}""".formatted(nombre, email)))
                .andReturn().getResponse().getContentAsString();
        return new Sesion(JsonPath.read(respuesta, "$.token"), ((Number) JsonPath.read(respuesta, "$.usuario.id")).longValue(),
                JsonPath.read(respuesta, "$.usuario.email"));
    }

    protected Sesion registrar() throws Exception {
        return registrar("Ana Prueba", "ana-" + UUID.randomUUID() + "@correo.es");
    }

    // ------------------------------------------------------------------ peticiones

    protected ResultActions obtener(Sesion s, String url) throws Exception {
        return mvc.perform(autenticar(get(url), s));
    }

    protected ResultActions enviar(Sesion s, String url, String json) throws Exception {
        return mvc.perform(autenticar(post(url), s).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions reemplazar(Sesion s, String url, String json) throws Exception {
        return mvc.perform(autenticar(put(url), s).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions modificar(Sesion s, String url, String json) throws Exception {
        return mvc.perform(autenticar(patch(url), s).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions borrar(Sesion s, String url) throws Exception {
        return mvc.perform(autenticar(delete(url), s));
    }

    protected ResultActions subir(Sesion s, String url, String nombre, byte[] contenido) throws Exception {
        return mvc.perform(autenticar(multipart(url).file(new MockMultipartFile("file", nombre, "application/octet-stream", contenido)), s));
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B autenticar(B peticion, Sesion s) {
        return s == null ? peticion : peticion.header("Authorization", "Bearer " + s.token());
    }

    protected static long idDe(ResultActions resultado) throws Exception {
        return ((Number) JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    // ------------------------------------------------------------------ datos de partida

    protected long crearCuenta(Sesion s, String banco, String saldoInicial) throws Exception {
        return idDe(enviar(s, "/usuarios/" + s.usuarioId() + "/cuentas", """
                {"banco":"%s","tipo":"NOMINA","alias":"Cuenta %s","moneda":"EUR","saldoInicial":%s}"""
                .formatted(banco, banco, saldoInicial)));
    }

    protected long crearCategoria(Sesion s, String nombre, String tipo) throws Exception {
        return idDe(enviar(s, "/categorias", """
                {"nombre":"%s","tipo":"%s"}""".formatted(nombre, tipo)));
    }

    /** Inserta un movimiento directamente en la base de datos, sin pasar por la importacion. */
    protected Movimiento movimiento(long cuentaId, LocalDate fecha, String importe, String descripcion, Categoria categoria) {
        CuentaBancaria cuenta = cuentaRepository.findById(cuentaId).orElseThrow();
        return movimientoRepository.save(Movimiento.builder().cuenta(cuenta).fecha(fecha).importe(new BigDecimal(importe))
                .descripcionOriginal(descripcion).categoria(categoria).origen(OrigenMovimiento.MANUAL)
                .hashDedup(UUID.randomUUID().toString()).build());
    }
}
