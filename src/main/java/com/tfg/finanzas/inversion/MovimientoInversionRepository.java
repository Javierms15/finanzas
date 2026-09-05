package com.tfg.finanzas.inversion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoInversionRepository extends JpaRepository<MovimientoInversion, Long> {

    List<MovimientoInversion> findByPosicionId(Long posicionId);
}
