package com.tfg.finanzas.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    static final String SECRETO = "secreto-de-pruebas-suficientemente-largo-para-hmac-sha-512-0123456789";

    final JwtService jwt = new JwtService(SECRETO, 60_000);

    @Test
    void generaYValidaUnTokenParaSuSujeto() {
        String token = jwt.generateToken("ana@correo.es");
        assertThat(jwt.extractSubject(token)).isEqualTo("ana@correo.es");
        assertThat(jwt.isValid(token, "ana@correo.es")).isTrue();
    }

    @Test
    void noEsValidoParaOtroSujeto() {
        assertThat(jwt.isValid(jwt.generateToken("ana@correo.es"), "otro@correo.es")).isFalse();
    }

    @Test
    void unTokenCaducadoNoEsValido() {
        JwtService caducado = new JwtService(SECRETO, -1_000);
        String token = caducado.generateToken("ana@correo.es");
        assertThat(caducado.isValid(token, "ana@correo.es")).isFalse();
    }

    @Test
    void unTokenFirmadoConOtraClaveNoEsValido() {
        JwtService ajeno = new JwtService("otra-clave-totalmente-distinta-de-al-menos-64-bytes-para-hmac-sha-512-xx", 60_000);
        String token = ajeno.generateToken("ana@correo.es");
        assertThat(jwt.isValid(token, "ana@correo.es")).isFalse();
    }

    @Test
    void unTokenManipuladoOBasuraNoEsValido() {
        String token = jwt.generateToken("ana@correo.es");
        assertThat(jwt.isValid(token + "x", "ana@correo.es")).isFalse();
        assertThat(jwt.isValid("esto-no-es-un-jwt", "ana@correo.es")).isFalse();
        assertThat(jwt.isValid("", "ana@correo.es")).isFalse();
    }

    @Test
    void extraerElSujetoDeBasuraLanzaExcepcion() {
        assertThatThrownBy(() -> jwt.extractSubject("basura")).isInstanceOf(RuntimeException.class);
    }
}
