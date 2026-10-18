package com.tfg.finanzas.service;

import com.tfg.finanzas.model.CuentaBancaria;
import com.tfg.finanzas.repository.CuentaBancariaRepository;
import com.tfg.finanzas.model.Movimiento;
import com.tfg.finanzas.repository.MovimientoRepository;
import com.tfg.finanzas.model.Usuario;
import com.tfg.finanzas.model.SnapshotPatrimonio;
import com.tfg.finanzas.repository.UsuarioRepository;
import com.tfg.finanzas.repository.SnapshotPatrimonioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatrimonioService {

    private final UsuarioRepository usuarioRepository;
    private final CuentaBancariaRepository cuentaBancariaRepository;
    private final MovimientoRepository movimientoRepository;
    private final InversionService inversionService;
    private final SnapshotPatrimonioRepository snapshotPatrimonioRepository;

    public BigDecimal calcularSaldoEfectivoTotal(Long usuarioId) {
        BigDecimal total = BigDecimal.ZERO;
        for (CuentaBancaria cuenta : cuentaBancariaRepository.findByUsuarioId(usuarioId)) {
            total = total.add(calcularSaldoCuenta(cuenta));
        }
        return total;
    }

    public BigDecimal calcularSaldoCuenta(CuentaBancaria cuenta) {
        BigDecimal sumaMovimientos = movimientoRepository.findByCuentaId(cuenta.getId()).stream()
                .map(Movimiento::getImporte)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return cuenta.getSaldoInicial().add(sumaMovimientos);
    }

    public Map<Long, BigDecimal> saldosPorCuenta(Long usuarioId) {
        return cuentaBancariaRepository.findByUsuarioId(usuarioId).stream()
                .collect(Collectors.toMap(CuentaBancaria::getId, this::calcularSaldoCuenta));
    }

    public BigDecimal calcularValorCarteraInversion(Long usuarioId) {
        return inversionService.valorTotalCartera(usuarioId);
    }

    /**
     * Guarda la foto del patrimonio de hoy. Solo la dispara el arranque de la aplicacion
     * (SnapshotAlArrancar): el usuario no puede crear fotos. Es UNA por dia y usuario: si ya hay una
     * de hoy (la app se arranco antes ese dia) se actualiza con los valores actuales en vez de
     * duplicarla. No se guarda nada si el patrimonio es 0 (usuario aun sin cuentas ni cartera).
     */
    @Transactional
    public Optional<SnapshotPatrimonio> registrarSnapshotDeHoy(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + usuarioId));
        BigDecimal saldoEfectivo = calcularSaldoEfectivoTotal(usuarioId);
        BigDecimal valorCartera = calcularValorCarteraInversion(usuarioId);
        if (saldoEfectivo.signum() == 0 && valorCartera.signum() == 0) {
            return Optional.empty();
        }
        LocalDate hoy = LocalDate.now();
        List<SnapshotPatrimonio> deHoy = snapshotPatrimonioRepository.findByUsuarioIdAndFecha(usuarioId, hoy);
        SnapshotPatrimonio snapshot = deHoy.isEmpty()
                ? SnapshotPatrimonio.builder().usuario(usuario).fecha(hoy).build()
                : deHoy.get(0);
        snapshot.setSaldoEfectivoTotal(saldoEfectivo);
        snapshot.setValorCarteraInversion(valorCartera);
        snapshot.setPatrimonioTotal(saldoEfectivo.add(valorCartera));
        if (deHoy.size() > 1) {
            snapshotPatrimonioRepository.deleteAll(deHoy.subList(1, deHoy.size()));
        }
        return Optional.of(snapshotPatrimonioRepository.save(snapshot));
    }

    public List<SnapshotPatrimonio> historico(Long usuarioId) {
        return snapshotPatrimonioRepository.findByUsuarioIdOrderByFechaAsc(usuarioId);
    }
}
