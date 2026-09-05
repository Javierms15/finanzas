package com.tfg.finanzas.movimiento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    List<Movimiento> findByCuentaId(Long cuentaId);

    List<Movimiento> findByCuentaIdAndFechaBetween(Long cuentaId, LocalDate desde, LocalDate hasta);

    boolean existsByHashDedup(String hashDedup);

    List<Movimiento> findByCategoriaIsNull();

    List<Movimiento> findByCuentaUsuarioId(Long usuarioId);
}
