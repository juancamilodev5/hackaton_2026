package com.hackaton.ulibre.comun.catalogo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface CatalogoRepository<E extends Catalogo> extends JpaRepository<E, UUID> {

    List<E> findAllByOrderByNombre();

    List<E> findAllByActivoOrderByNombre(boolean activo);
}
