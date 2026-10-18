package com.tfg.finanzas.repository;

import com.tfg.finanzas.model.MovimientoPrevisto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoPrevistoRepository extends JpaRepository<MovimientoPrevisto, Long> {

    List<MovimientoPrevisto> findByUsuarioIdOrderByFechaAsc(Long usuarioId);
}
