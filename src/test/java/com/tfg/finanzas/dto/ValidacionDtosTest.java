package com.tfg.finanzas.dto;

import com.tfg.finanzas.dto.AuthDtos.LoginRequest;
import com.tfg.finanzas.dto.AuthDtos.RegisterRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidacionDtosTest {

    final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void registroValidoNoTieneErrores() {
        assertThat(validador.validate(new RegisterRequest("Ana", "ana@correo.es", "contrasena1"))).isEmpty();
    }

    @Test
    void registroRechazaCamposVacios() {
        assertThat(validador.validate(new RegisterRequest("", "", ""))).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void registroRechazaEmailMalFormado() {
        assertThat(validador.validate(new RegisterRequest("Ana", "no-es-email", "contrasena1")))
                .extracting(v -> v.getMessage()).contains("El email no es válido");
    }

    @Test
    void registroRechazaContrasenaCortaYMasLargaQueBcrypt() {
        assertThat(validador.validate(new RegisterRequest("Ana", "a@b.es", "corta"))).isNotEmpty();
        assertThat(validador.validate(new RegisterRequest("Ana", "a@b.es", "x".repeat(73)))).isNotEmpty();
        assertThat(validador.validate(new RegisterRequest("Ana", "a@b.es", "x".repeat(72)))).isEmpty();
        assertThat(validador.validate(new RegisterRequest("Ana", "a@b.es", "x".repeat(8)))).isEmpty();
    }

    @Test
    void registroRechazaNombreDemasiadoLargo() {
        assertThat(validador.validate(new RegisterRequest("n".repeat(81), "a@b.es", "contrasena1"))).isNotEmpty();
    }

    @Test
    void loginNoExigeLongitudMinimaDeContrasena() {
        assertThat(validador.validate(new LoginRequest("a@b.es", "x"))).isEmpty();
        assertThat(validador.validate(new LoginRequest("a@b.es", ""))).isNotEmpty();
    }
}
