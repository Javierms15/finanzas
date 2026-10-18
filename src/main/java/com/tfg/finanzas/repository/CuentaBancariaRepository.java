package com.tfg.finanzas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.tfg.finanzas.model.CuentaBancaria;

import java.util.List;

public interface CuentaBancariaRepository extends JpaRepository<CuentaBancaria, Long> {

    List<CuentaBancaria> findByUsuarioId(Long usuarioId);
}
