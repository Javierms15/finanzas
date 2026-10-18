package com.tfg.finanzas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.tfg.finanzas.model.Activo;

import java.util.Optional;

public interface ActivoRepository extends JpaRepository<Activo, Long> {

    Optional<Activo> findByTicker(String ticker);
}
