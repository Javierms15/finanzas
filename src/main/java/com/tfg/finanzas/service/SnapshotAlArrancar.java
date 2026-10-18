package com.tfg.finanzas.service;

import com.tfg.finanzas.model.Usuario;
import com.tfg.finanzas.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Cada vez que arranca la aplicacion guarda la foto del patrimonio de cada usuario (la historia de la
 * pantalla Evolucion se construye asi, sin que nadie tenga que acordarse). Va en un hilo aparte y
 * DESPUES de que el servidor ya responde: valorar la cartera consulta precios por red y no debe
 * retrasar el arranque. Un fallo con un usuario no impide la foto de los demas.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "finanzas.snapshot-al-arrancar", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SnapshotAlArrancar {

    private final UsuarioRepository usuarioRepository;
    private final PatrimonioService patrimonioService;

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        Thread.ofVirtual().name("snapshot-al-arrancar").start(this::registrarParaTodos);
    }

    void registrarParaTodos() {
        for (Usuario usuario : usuarioRepository.findAll()) {
            try {
                patrimonioService.registrarSnapshotDeHoy(usuario.getId()).ifPresent(s ->
                        log.info("Foto del patrimonio de {}: {} EUR", usuario.getEmail(), s.getPatrimonioTotal()));
            } catch (RuntimeException e) {
                log.warn("No se pudo guardar la foto del patrimonio de {}: {}", usuario.getEmail(), e.getMessage());
            }
        }
    }
}
