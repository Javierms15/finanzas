package com.tfg.finanzas.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActividadClienteTest {

    final ActividadCliente actividad = new ActividadCliente();

    @Test
    void alPrincipioNoHayActividadNiCierrePendiente() {
        var estado = actividad.estado();
        assertThat(estado.latidos()).isZero();
        assertThat(estado.segundosDesdeLatido()).isNull();
        assertThat(estado.segundosDesdeCierre()).isNull();
        assertThat(estado.cierrePendiente()).isFalse();
    }

    @Test
    void losLatidosSeContabilizan() {
        actividad.latido();
        actividad.latido();
        var estado = actividad.estado();
        assertThat(estado.latidos()).isEqualTo(2);
        assertThat(estado.segundosDesdeLatido()).isNotNull().isLessThan(5);
        assertThat(estado.cierrePendiente()).isFalse();
    }

    @Test
    void unCierreSinLatidosPosterioresQuedaPendiente() throws InterruptedException {
        actividad.latido();
        Thread.sleep(5);
        actividad.cierre();
        assertThat(actividad.estado().cierrePendiente()).isTrue();
    }

    @Test
    void unLatidoPosteriorAlCierreLoAnula() throws InterruptedException {
        actividad.cierre();
        Thread.sleep(5);
        actividad.latido();
        assertThat(actividad.estado().cierrePendiente()).isFalse();
    }

    @Test
    void unCierreSinNingunLatidoPreviosEstaPendiente() {
        actividad.cierre();
        assertThat(actividad.estado().cierrePendiente()).isTrue();
    }
}
