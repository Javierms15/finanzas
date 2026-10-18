package com.tfg.finanzas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.tfg.finanzas.model.Movimiento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    List<Movimiento> findByCuentaId(Long cuentaId);

    List<Movimiento> findByCuentaIdAndFechaBetween(Long cuentaId, LocalDate desde, LocalDate hasta);

    boolean existsByHashDedup(String hashDedup);

    boolean existsByCuentaIdAndFechaAndImporte(Long cuentaId, LocalDate fecha, BigDecimal importe);

    List<Movimiento> findByCategoriaIsNull();

    List<Movimiento> findByCuentaUsuarioId(Long usuarioId);
}
