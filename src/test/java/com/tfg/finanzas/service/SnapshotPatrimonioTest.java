package com.tfg.finanzas.service;

import com.tfg.finanzas.model.SnapshotPatrimonio;
import com.tfg.finanzas.repository.SnapshotPatrimonioRepository;
import com.tfg.finanzas.repository.UsuarioRepository;
import com.tfg.finanzas.support.ApiBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SnapshotPatrimonioTest extends ApiBase {

    @Autowired
    PatrimonioService patrimonio;
    @Autowired
    SnapshotPatrimonioRepository snapshots;
    @Autowired
    UsuarioRepository usuarios;

    @Test
    void sinCuentasNiCarteraNoSeGuardaNingunaFoto() throws Exception {
        Sesion s = registrar();

        assertThat(patrimonio.registrarSnapshotDeHoy(s.usuarioId())).isEmpty();
        assertThat(patrimonio.historico(s.usuarioId())).isEmpty();
    }

    @Test
    void laFotoGuardaEfectivoCarteraYTotal() throws Exception {
        Sesion s = registrar();
        long cuenta = crearCuenta(s, "ING", "1000");
        movimiento(cuenta, LocalDate.of(2026, 1, 1), "250.50", "Ingreso", null);

        Optional<SnapshotPatrimonio> foto = patrimonio.registrarSnapshotDeHoy(s.usuarioId());

        assertThat(foto).isPresent();
        assertThat(foto.get().getFecha()).isEqualTo(LocalDate.now());
        assertThat(foto.get().getSaldoEfectivoTotal()).isEqualByComparingTo("1250.50");
        assertThat(foto.get().getValorCarteraInversion()).isEqualByComparingTo("0");
        assertThat(foto.get().getPatrimonioTotal()).isEqualByComparingTo("1250.50");
    }

    @Test
    void soloHayUnaFotoPorDiaYSeActualizaConLosValoresNuevos() throws Exception {
        Sesion s = registrar();
        long cuenta = crearCuenta(s, "ING", "1000");
        patrimonio.registrarSnapshotDeHoy(s.usuarioId());

        movimiento(cuenta, LocalDate.of(2026, 1, 1), "500", "Ingreso", null);
        patrimonio.registrarSnapshotDeHoy(s.usuarioId());

        List<SnapshotPatrimonio> historico = patrimonio.historico(s.usuarioId());
        assertThat(historico).hasSize(1);
        assertThat(historico.get(0).getPatrimonioTotal()).isEqualByComparingTo("1500");
    }

    @Test
    void siHabiaFotosDuplicadasDeHoyQuedaSoloUna() throws Exception {
        Sesion s = registrar();
        crearCuenta(s, "ING", "1000");
        var usuario = usuarios.findById(s.usuarioId()).orElseThrow();
        for (int i = 0; i < 3; i++) {
            snapshots.save(SnapshotPatrimonio.builder().usuario(usuario).fecha(LocalDate.now())
                    .saldoEfectivoTotal(BigDecimal.ONE).valorCarteraInversion(BigDecimal.ZERO).patrimonioTotal(BigDecimal.ONE).build());
        }

        patrimonio.registrarSnapshotDeHoy(s.usuarioId());

        assertThat(snapshots.findByUsuarioIdAndFecha(s.usuarioId(), LocalDate.now())).hasSize(1);
    }

    @Test
    void elHistoricoVieneOrdenadoPorFechaYSeLeePorLaApi() throws Exception {
        Sesion s = registrar();
        crearCuenta(s, "ING", "100");
        var usuario = usuarios.findById(s.usuarioId()).orElseThrow();
        snapshots.save(SnapshotPatrimonio.builder().usuario(usuario).fecha(LocalDate.of(2026, 5, 2))
                .saldoEfectivoTotal(BigDecimal.TEN).valorCarteraInversion(BigDecimal.ZERO).patrimonioTotal(BigDecimal.TEN).build());
        snapshots.save(SnapshotPatrimonio.builder().usuario(usuario).fecha(LocalDate.of(2026, 5, 1))
                .saldoEfectivoTotal(BigDecimal.ONE).valorCarteraInversion(BigDecimal.ZERO).patrimonioTotal(BigDecimal.ONE).build());

        obtener(s, "/usuarios/" + s.usuarioId() + "/patrimonio/snapshots")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fecha").value("2026-05-01"))
                .andExpect(jsonPath("$[1].fecha").value("2026-05-02"));
    }

    @Test
    void alArrancarSeGuardaLaFotoDeTodosLosUsuariosConPatrimonioYSoloDeEsos() throws Exception {
        Sesion conDinero = registrar();
        crearCuenta(conDinero, "ING", "500");
        Sesion sinDinero = registrar();

        new SnapshotAlArrancar(usuarios, patrimonio).registrarParaTodos();

        assertThat(patrimonio.historico(conDinero.usuarioId())).hasSize(1);
        assertThat(patrimonio.historico(sinDinero.usuarioId())).isEmpty();
    }
}
