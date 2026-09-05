package com.tfg.finanzas.cashback;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashbackAcumuladoRepository extends JpaRepository<CashbackAcumulado, Long> {

    List<CashbackAcumulado> findByCuentaId(Long cuentaId);
}
