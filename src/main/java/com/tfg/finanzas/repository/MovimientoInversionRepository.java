package com.tfg.finanzas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.tfg.finanzas.model.MovimientoInversion;

import java.util.List;

public interface MovimientoInversionRepository extends JpaRepository<MovimientoInversion, Long> {

    List<MovimientoInversion> findByPosicionId(Long posicionId);
}
