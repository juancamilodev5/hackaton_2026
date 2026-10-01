package com.hackaton.ulibre.comun.catalogo;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.Textos;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD de un catálogo simple. "Eliminar" desactiva (activo = false): los catálogos con histórico
 * no se borran (regla 13) y las FKs sin CASCADE lo impedirían de todos modos.
 */
public abstract class CatalogoService<E extends Catalogo> {

    private final CatalogoRepository<E> repositorio;
    private final Supplier<E> nuevo;
    private final String noEncontrado;

    protected CatalogoService(CatalogoRepository<E> repositorio, Supplier<E> nuevo, String noEncontrado) {
        this.repositorio = repositorio;
        this.nuevo = nuevo;
        this.noEncontrado = noEncontrado;
    }

    @Transactional(readOnly = true)
    public List<CatalogoResponse> listar(Boolean activo) {
        List<E> filas = activo == null ? repositorio.findAllByOrderByNombre()
                : repositorio.findAllByActivoOrderByNombre(activo);
        return filas.stream().map(CatalogoResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public CatalogoResponse obtener(UUID id) {
        return CatalogoResponse.de(buscar(id));
    }

    @Transactional
    public CatalogoResponse crear(CatalogoRequest datos) {
        E entidad = nuevo.get();
        entidad.setActivo(true);
        aplicar(entidad, datos);
        return CatalogoResponse.de(repositorio.saveAndFlush(entidad));
    }

    @Transactional
    public CatalogoResponse actualizar(UUID id, CatalogoRequest datos) {
        E entidad = buscar(id);
        aplicar(entidad, datos);
        return CatalogoResponse.de(repositorio.saveAndFlush(entidad));
    }

    @Transactional
    public void desactivar(UUID id) {
        E entidad = buscar(id);
        entidad.setActivo(false);
        repositorio.saveAndFlush(entidad);
    }

    /** También lo usan otros servicios para validar referencias con un 404 claro. */
    @Transactional(readOnly = true)
    public E buscar(UUID id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(noEncontrado + ": " + id));
    }

    private static void aplicar(Catalogo entidad, CatalogoRequest datos) {
        entidad.setCodigo(Textos.codigo(datos.codigo()));
        entidad.setNombre(Textos.limpiar(datos.nombre()));
        entidad.setDescripcion(Textos.limpiar(datos.descripcion()));
        if (datos.activo() != null) {
            entidad.setActivo(datos.activo());
        }
    }
}
