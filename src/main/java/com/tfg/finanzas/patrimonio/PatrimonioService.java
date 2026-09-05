package com.tfg.finanzas.patrimonio;

import com.tfg.finanzas.cuenta.CuentaBancaria;
import com.tfg.finanzas.cuenta.CuentaBancariaRepository;
import com.tfg.finanzas.inversion.InversionService;
import com.tfg.finanzas.movimiento.Movimiento;
import com.tfg.finanzas.movimiento.MovimientoRepository;
import com.tfg.finanzas.usuario.Usuario;
import com.tfg.finanzas.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
            BigDecimal sumaMovimientos = movimientoRepository.findByCuentaId(cuenta.getId()).stream()
                    .map(Movimiento::getImporte)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            total = total.add(cuenta.getSaldoInicial()).add(sumaMovimientos);
        }
        return total;
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
