package com.tfg.finanzas.inversion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ActivoRepository extends JpaRepository<Activo, Long> {

    Optional<Activo> findByTicker(String ticker);
}
