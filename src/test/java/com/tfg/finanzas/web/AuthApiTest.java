package com.tfg.finanzas.web;

import com.tfg.finanzas.repository.UsuarioRepository;
import com.tfg.finanzas.security.JwtService;
import com.tfg.finanzas.support.ApiBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends ApiBase {

    @Autowired
    UsuarioRepository usuarios;
    @Autowired
    JwtService jwt;

    private String json(String nombre, String email, String password) {
        return """
                {"nombre":"%s","email":"%s","password":"%s"}""".formatted(nombre, email, password);
    }

    // ------------------------------------------------------------------ registro

    @Test
    void registrarDevuelve201ConTokenYUsuarioSinContrasena() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Ana", "ana@correo.es", "una-contrasena")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.email").value("ana@correo.es"))
                .andExpect(jsonPath("$.usuario.nombre").value("Ana"))
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("una-contrasena"))));
    }

    @Test
    void laContrasenaSeGuardaCifradaConBcrypt() throws Exception {
        registrar("Ana", "cifrada@correo.es");
        String hash = usuarios.findByEmail("cifrada@correo.es").orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("$2").isNotEqualTo("contrasena-segura-1");
    }

    @Test
    void elEmailSeGuardaEnMinusculas() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Ana", "ANA.Mayus@Correo.ES", "una-contrasena")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario.email").value("ana.mayus@correo.es"));
    }

    @Test
    void unEmailConEspaciosSeRechazaComoNoValido() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Ana", " ana@correo.es ", "una-contrasena")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El email no es válido"));
    }

    @Test
    void unEmailRepetidoDevuelve409IgnorandoMayusculas() throws Exception {
        registrar("Ana", "dup@correo.es");
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Otra", "DUP@correo.es", "una-contrasena")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe una cuenta con ese email"));
    }

    @Test
    void registrarValidaLosCamposYExplicaElMotivo() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Ana", "no-es-un-email", "una-contrasena")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El email no es válido"));

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Ana", "corta@correo.es", "corta")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La contraseña debe tener entre 8 y 72 caracteres"));

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Ana", "larga@correo.es", "x".repeat(73))))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ login

    @Test
    void loginCorrectoDevuelveTokenValido() throws Exception {
        registrar("Ana", "login@correo.es");
        String respuesta = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"LOGIN@correo.es","password":"contrasena-segura-1"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.email").value("login@correo.es"))
                .andReturn().getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(respuesta, "$.token");
        assertThat(jwt.isValid(token, "login@correo.es")).isTrue();
    }

    @Test
    void loginIncorrectoDevuelve401ConElMismoMensajeSeaCualSeaElMotivo() throws Exception {
        registrar("Ana", "existe@correo.es");
        for (String cuerpo : new String[]{
                """
                {"email":"existe@correo.es","password":"mala-contrasena"}""",
                """
                {"email":"no-existe@correo.es","password":"contrasena-segura-1"}"""}) {
            mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Email o contraseña incorrectos"));
        }
    }

    // ------------------------------------------------------------------ proteccion de rutas

    @Test
    void sinTokenLasRutasProtegidasDevuelven401() throws Exception {
        mvc.perform(get("/usuarios/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/categorias")).andExpect(status().isUnauthorized());
        mvc.perform(get("/usuarios/1/cuentas")).andExpect(status().isUnauthorized());
    }

    @Test
    void conUnTokenBasuraOFirmadoPorOtroDevuelve401() throws Exception {
        mvc.perform(get("/usuarios/me").header("Authorization", "Bearer basura")).andExpect(status().isUnauthorized());
        mvc.perform(get("/usuarios/me").header("Authorization", "Basic abc")).andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenDeUnUsuarioQueYaNoExisteDevuelve401EnVezDeError500() throws Exception {
        Sesion s = registrar("Ana", "borrado@correo.es");
        usuarios.deleteById(s.usuarioId());
        usuarios.flush();

        obtener(s, "/usuarios/me").andExpect(status().isUnauthorized());
    }

    @Test
    void meDevuelveSoloAlUsuarioDeLaSesion() throws Exception {
        registrar("Otra Persona", "otra@correo.es");
        Sesion ana = registrar("Ana", "ana-me@correo.es");

        obtener(ana, "/usuarios/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana-me@correo.es"))
                .andExpect(jsonPath("$.nombre").value("Ana"));
    }

    @Test
    void yaNoExisteLaListaDeTodosLosUsuarios() throws Exception {
        Sesion ana = registrar();
        obtener(ana, "/usuarios").andExpect(status().is4xxClientError());
    }

    // ------------------------------------------------------------------ cambio de contrasena

    @Test
    void cambiarContrasenaPermiteEntrarConLaNuevaYNoConLaAntigua() throws Exception {
        Sesion s = registrar("Ana", "cambio@correo.es");

        reemplazar(s, "/usuarios/me/password", """
                {"passwordActual":"contrasena-segura-1","passwordNueva":"otra-contrasena-2"}""")
                .andExpect(status().isNoContent());

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cambio@correo.es","password":"otra-contrasena-2"}"""))
                .andExpect(status().isOk());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cambio@correo.es","password":"contrasena-segura-1"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cambiarContrasenaConLaActualIncorrectaDevuelve400NoEchaAlUsuario() throws Exception {
        Sesion s = registrar();
        reemplazar(s, "/usuarios/me/password", """
                {"passwordActual":"no-es-esta","passwordNueva":"otra-contrasena-2"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La contraseña actual no es correcta"));
    }

    @Test
    void cambiarContrasenaRechazaLaMismaYLasDemasiadoCortas() throws Exception {
        Sesion s = registrar();
        reemplazar(s, "/usuarios/me/password", """
                {"passwordActual":"contrasena-segura-1","passwordNueva":"contrasena-segura-1"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La contraseña nueva debe ser distinta de la actual"));
        reemplazar(s, "/usuarios/me/password", """
                {"passwordActual":"contrasena-segura-1","passwordNueva":"corta"}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void cambiarContrasenaExigeSesion() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/usuarios/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"passwordActual":"a","passwordNueva":"bbbbbbbb"}"""))
                .andExpect(status().isUnauthorized());
    }
}
