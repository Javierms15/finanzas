package com.tfg.finanzas.cuenta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CuentaBancariaRepository extends JpaRepository<CuentaBancaria, Long> {

    List<CuentaBancaria> findByUsuarioId(Long usuarioId);
}
