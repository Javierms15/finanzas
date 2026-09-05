package com.tfg.finanzas.patrimonio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SnapshotPatrimonioRepository extends JpaRepository<SnapshotPatrimonio, Long> {

    List<SnapshotPatrimonio> findByUsuarioIdOrderByFechaAsc(Long usuarioId);

    Optional<SnapshotPatrimonio> findTopByUsuarioIdOrderByFechaDesc(Long usuarioId);
}
