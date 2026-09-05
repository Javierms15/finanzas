package com.tfg.finanzas.cashback;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TarjetaBeneficioRepository extends JpaRepository<TarjetaBeneficio, Long> {

    List<TarjetaBeneficio> findByCuentaId(Long cuentaId);
}
