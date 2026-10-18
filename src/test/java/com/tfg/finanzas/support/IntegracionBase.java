package com.tfg.finanzas.support;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base de los tests de integracion: aplicacion completa contra un PostgreSQL REAL (el mismo motor que en
 * produccion; varios fallos reales del proyecto eran propios de Postgres: escala de decimales, restricciones
 * CHECK de los enum, tipo text). El contenedor es unico para todos los tests de la ejecucion, y cada test corre
 * dentro de una transaccion que se deshace al terminar, asi que no se contaminan entre si.
 * <p>
 * Si Docker no esta disponible estos tests se OMITEN (los unitarios siguen ejecutandose).
 */
@SpringBootTest
@Transactional
@EnabledIf(value = "com.tfg.finanzas.support.IntegracionBase#dockerDisponible", disabledReason = "Docker no esta disponible")
public abstract class IntegracionBase {

    private static final PostgreSQLContainer POSTGRES;

    static {
        if (dockerDisponible()) {
            POSTGRES = new PostgreSQLContainer("postgres:17").withDatabaseName("finanzas_test")
                    .withUsername("finanzas").withPassword("finanzas");
            POSTGRES.start();
        } else {
            POSTGRES = null;
        }
    }

    public static boolean dockerDisponible() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        if (POSTGRES == null) {
            return;
        }
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
        registro.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registro.add("spring.jpa.show-sql", () -> "false");
        registro.add("finanzas.snapshot-al-arrancar", () -> "false");
        registro.add("jwt.secret", () -> "secreto-de-pruebas-suficientemente-largo-para-hmac-sha-512-0123456789");
    }
}
