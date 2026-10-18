package com.tfg.finanzas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.tfg.finanzas.model.CashbackAcumulado;

import java.util.List;

public interface CashbackAcumuladoRepository extends JpaRepository<CashbackAcumulado, Long> {

    List<CashbackAcumulado> findByCuentaId(Long cuentaId);
}
