package com.tfg.finanzas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.tfg.finanzas.model.TarjetaBeneficio;

import java.util.List;

public interface TarjetaBeneficioRepository extends JpaRepository<TarjetaBeneficio, Long> {

    List<TarjetaBeneficio> findByCuentaId(Long cuentaId);
}
