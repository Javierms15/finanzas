package com.tfg.finanzas.categoria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReglaCategorizacionRepository extends JpaRepository<ReglaCategorizacion, Long> {

    List<ReglaCategorizacion> findAllByOrderByPrioridadAsc();
}
