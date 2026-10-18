package com.tfg.finanzas;

import com.tfg.finanzas.repository.UsuarioRepository;
import com.tfg.finanzas.support.IntegracionBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ContextoIntegracionTest extends IntegracionBase {

    @Autowired
    UsuarioRepository usuarioRepository;

    @Test
    void elContextoArrancaContraPostgresRealYElEsquemaSeCrea() {
        assertThat(usuarioRepository.count()).isZero();
    }
}
