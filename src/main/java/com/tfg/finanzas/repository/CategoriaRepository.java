package com.tfg.finanzas.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import com.tfg.finanzas.model.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    Optional<Categoria> findFirstByNombreIgnoreCase(String nombre);
}
