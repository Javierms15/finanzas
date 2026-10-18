package com.tfg.finanzas.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Registra si hay una pagina de Finanzas abierta: la web envia un latido cada pocos segundos
 * y un aviso de cierre al descargarse la pagina. Lo consulta el lanzador de escritorio para
 * apagar todo cuando ya no hay ninguna pagina abierta, sin depender de como se abrio la web
 * (ventana de aplicacion, pestana normal...). Solo guarda tiempos, ningun dato del usuario.
 */
@Component
public class ActividadCliente {

    private final AtomicReference<Instant> ultimoLatido = new AtomicReference<>();
    private final AtomicReference<Instant> ultimoCierre = new AtomicReference<>();
    private final AtomicLong latidos = new AtomicLong();

    public void latido() {
        ultimoLatido.set(Instant.now());
        latidos.incrementAndGet();
    }

    public void cierre() {
        ultimoCierre.set(Instant.now());
    }

    /**
     * cierrePendiente: hubo un aviso de cierre y desde entonces no ha llegado ningun latido.
     * Si es solo una recarga (o cierra una pestana de varias abiertas), el siguiente latido lo anula.
     */
    public Estado estado() {
        Instant ahora = Instant.now();
        Instant latido = ultimoLatido.get();
        Instant cierre = ultimoCierre.get();
        boolean cierrePendiente = cierre != null && (latido == null || cierre.isAfter(latido));
        return new Estado(latidos.get(),
                latido != null ? Duration.between(latido, ahora).toSeconds() : null,
                cierre != null ? Duration.between(cierre, ahora).toSeconds() : null,
                cierrePendiente);
    }

    public record Estado(long latidos, Long segundosDesdeLatido, Long segundosDesdeCierre, boolean cierrePendiente) {
    }
}
