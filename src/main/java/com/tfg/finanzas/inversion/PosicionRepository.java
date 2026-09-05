package com.tfg.finanzas.inversion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PosicionRepository extends JpaRepository<Posicion, Long> {

    List<Posicion> findByUsuarioId(Long usuarioId);

    Optional<Posicion> findByUsuarioIdAndActivoId(Long usuarioId, Long activoId);
}
