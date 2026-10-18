package com.tfg.finanzas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.tfg.finanzas.model.SnapshotPatrimonio;

import java.util.List;
import java.util.Optional;

public interface SnapshotPatrimonioRepository extends JpaRepository<SnapshotPatrimonio, Long> {

    List<SnapshotPatrimonio> findByUsuarioIdOrderByFechaAsc(Long usuarioId);

    Optional<SnapshotPatrimonio> findTopByUsuarioIdOrderByFechaDesc(Long usuarioId);
}
