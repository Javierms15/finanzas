package com.tfg.finanzas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.tfg.finanzas.model.ReglaCategorizacion;

import java.util.List;

public interface ReglaCategorizacionRepository extends JpaRepository<ReglaCategorizacion, Long> {

    List<ReglaCategorizacion> findAllByOrderByPrioridadAsc();
}
