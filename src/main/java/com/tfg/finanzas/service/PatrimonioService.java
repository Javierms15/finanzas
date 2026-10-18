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

    @Transactional
    public SnapshotPatrimonio generarSnapshot(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + usuarioId));
        BigDecimal saldoEfectivo = calcularSaldoEfectivoTotal(usuarioId);
        BigDecimal valorCartera = calcularValorCarteraInversion(usuarioId);
        SnapshotPatrimonio snapshot = SnapshotPatrimonio.builder()
                .usuario(usuario)
                .fecha(LocalDate.now())
                .saldoEfectivoTotal(saldoEfectivo)
                .valorCarteraInversion(valorCartera)
                .patrimonioTotal(saldoEfectivo.add(valorCartera))
                .build();
        return snapshotPatrimonioRepository.save(snapshot);
    }

    public List<SnapshotPatrimonio> historico(Long usuarioId) {
        return snapshotPatrimonioRepository.findByUsuarioIdOrderByFechaAsc(usuarioId);
    }
}
