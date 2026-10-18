package com.tfg.finanzas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.tfg.finanzas.model.Posicion;

import java.util.List;
import java.util.Optional;

public interface PosicionRepository extends JpaRepository<Posicion, Long> {

    List<Posicion> findByUsuarioId(Long usuarioId);

    Optional<Posicion> findByUsuarioIdAndActivoId(Long usuarioId, Long activoId);
}
