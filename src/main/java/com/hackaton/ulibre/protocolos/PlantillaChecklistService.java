package com.hackaton.ulibre.protocolos;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.hackaton.ulibre.auditoria.AccionAuditoria;
import com.hackaton.ulibre.auditoria.Auditoria;
import com.hackaton.ulibre.catalogos.RolClinico;
import com.hackaton.ulibre.catalogos.RolClinicoRepository;
import com.hackaton.ulibre.catalogos.RolClinicoVista;
import com.hackaton.ulibre.comun.RecursoNoEncontradoException;
import com.hackaton.ulibre.comun.ReglaNegocioException;
import com.hackaton.ulibre.comun.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Protocolos de checklist. La inmutabilidad de lo publicado (regla 10) la imponen los triggers
 * tg_plantillas_proteger, tg_fases_plantilla_proteger y tg_items_plantilla_proteger: aquí no se
 * repite esa comprobación, el error de la base sale como 422. Java solo valida las transiciones
 * (publicar desde BORRADOR, retirar desde PUBLICADA) y que lo que se publica tenga contenido.
 * Cada cambio queda en registros_auditoria.
 */
@Service
public class PlantillaChecklistService {

    private static final String TABLA = "plantillas_checklist";
    private static final String TABLA_FASES = "fases_plantilla_checklist";
    private static final String TABLA_ITEMS = "items_plantilla_checklist";

    private final PlantillaChecklistRepository plantillas;
    private final FasePlantillaChecklistRepository fases;
    private final ItemPlantillaChecklistRepository items;
    private final RolClinicoRepository roles;
    private final Clock clock;
    private final Auditoria auditoria;

    public PlantillaChecklistService(PlantillaChecklistRepository plantillas, FasePlantillaChecklistRepository fases,
            ItemPlantillaChecklistRepository items, RolClinicoRepository roles, Clock clock, Auditoria auditoria) {
        this.auditoria = auditoria;
        this.plantillas = plantillas;
        this.fases = fases;
        this.items = items;
        this.roles = roles;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- plantillas

    /** Pocas filas: se filtra en memoria. Orden: código y versión descendente (la más nueva primero). */
    @Transactional(readOnly = true)
    public List<PlantillaResponse> listar(String codigo, EstadoPlantilla estado) {
        String filtroCodigo = Textos.codigo(codigo);
        return plantillas.findAll().stream()
                .filter(p -> filtroCodigo == null || p.getCodigo().equals(filtroCodigo))
                .filter(p -> estado == null || p.getEstado() == estado)
                .sorted(Comparator.comparing(PlantillaChecklist::getCodigo)
                        .thenComparing(PlantillaChecklist::getVersion, Comparator.reverseOrder()))
                .map(PlantillaResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlantillaDetalleResponse obtener(UUID id) {
        return detalle(buscarPlantilla(id));
    }

    @Transactional
    public PlantillaDetalleResponse crear(PlantillaRequest datos) {
        PlantillaChecklist plantilla = new PlantillaChecklist();
        plantilla.setCodigo(Textos.codigo(datos.codigo()));
        plantilla.setNombre(Textos.limpiar(datos.nombre()));
        plantilla.setDescripcion(Textos.limpiar(datos.descripcion()));
        plantilla.setVersion(plantillas.maximaVersion(plantilla.getCodigo()) + 1);
        plantilla.setEstado(EstadoPlantilla.BORRADOR);
        plantillas.saveAndFlush(plantilla);
        auditoria.registrar(TABLA, plantilla.getId(), AccionAuditoria.CREAR, null, PlantillaResponse.de(plantilla));
        return detalle(plantilla);
    }

    @Transactional
    public PlantillaDetalleResponse actualizar(UUID id, PlantillaRequest datos) {
        PlantillaChecklist plantilla = buscarPlantilla(id);
        PlantillaResponse anterior = PlantillaResponse.de(plantilla);
        plantilla.setCodigo(Textos.codigo(datos.codigo()));
        plantilla.setNombre(Textos.limpiar(datos.nombre()));
        plantilla.setDescripcion(Textos.limpiar(datos.descripcion()));
        plantillas.saveAndFlush(plantilla);
        auditoria.registrar(TABLA, id, AccionAuditoria.ACTUALIZAR, anterior, PlantillaResponse.de(plantilla));
        return detalle(plantilla);
    }

    /** Borra ítems, fases y plantilla; los triggers lo rechazan si no es BORRADOR. */
    @Transactional
    public void eliminar(UUID id) {
        PlantillaChecklist plantilla = buscarPlantilla(id);
        PlantillaDetalleResponse anterior = detalle(plantilla);
        List<FasePlantillaChecklist> suyas = fases.findAllByPlantillaIdOrderByOrdenAscCodigoAsc(id);
        items.deleteAllInBatch(items.findAllByFaseIdInOrderByOrdenAscCodigoAsc(ids(suyas)));
        fases.deleteAllInBatch(suyas);
        plantillas.delete(plantilla);
        plantillas.flush();
        auditoria.registrar(TABLA, id, AccionAuditoria.ELIMINAR, anterior, null);
    }

    @Transactional
    public PlantillaDetalleResponse publicar(UUID id) {
        PlantillaChecklist plantilla = buscarPlantilla(id);
        if (plantilla.getEstado() != EstadoPlantilla.BORRADOR) {
            throw new ReglaNegocioException("Solo se publica una plantilla en BORRADOR; esta está " + plantilla.getEstado());
        }
        List<FasePlantillaChecklist> suyas = fases.findAllByPlantillaIdOrderByOrdenAscCodigoAsc(id);
        if (suyas.isEmpty()) {
            throw new ReglaNegocioException("La plantilla no tiene fases");
        }
        Set<UUID> fasesConItems = items.findAllByFaseIdInOrderByOrdenAscCodigoAsc(ids(suyas)).stream()
                .map(ItemPlantillaChecklist::getFaseId)
                .collect(Collectors.toSet());
        List<String> vacias = suyas.stream()
                .filter(f -> !fasesConItems.contains(f.getId()))
                .map(FasePlantillaChecklist::getCodigo)
                .toList();
        if (!vacias.isEmpty()) {
            throw new ReglaNegocioException("Fases sin ítems: " + String.join(", ", vacias));
        }
        PlantillaResponse anterior = PlantillaResponse.de(plantilla);
        plantilla.setEstado(EstadoPlantilla.PUBLICADA);
        plantilla.setPublicadaEn(LocalDateTime.now(clock));
        plantillas.saveAndFlush(plantilla);
        auditoria.registrar(TABLA, id, AccionAuditoria.PUBLICAR, anterior, PlantillaResponse.de(plantilla));
        return detalle(plantilla);
    }

    @Transactional
    public PlantillaDetalleResponse retirar(UUID id) {
        PlantillaChecklist plantilla = buscarPlantilla(id);
        if (plantilla.getEstado() != EstadoPlantilla.PUBLICADA) {
            throw new ReglaNegocioException("Solo se retira una plantilla PUBLICADA; esta está " + plantilla.getEstado());
        }
        PlantillaResponse anterior = PlantillaResponse.de(plantilla);
        plantilla.setEstado(EstadoPlantilla.RETIRADA);
        plantillas.saveAndFlush(plantilla);
        auditoria.registrar(TABLA, id, AccionAuditoria.RETIRAR, anterior, PlantillaResponse.de(plantilla));
        return detalle(plantilla);
    }

    /** Copia plantilla, fases e ítems a un BORRADOR con la siguiente versión del mismo código. */
    @Transactional
    public PlantillaDetalleResponse nuevaVersion(UUID id) {
        PlantillaChecklist origen = buscarPlantilla(id);
        PlantillaChecklist copia = new PlantillaChecklist();
        copia.setCodigo(origen.getCodigo());
        copia.setNombre(origen.getNombre());
        copia.setDescripcion(origen.getDescripcion());
        copia.setVersion(plantillas.maximaVersion(origen.getCodigo()) + 1);
        copia.setEstado(EstadoPlantilla.BORRADOR);
        copia = plantillas.saveAndFlush(copia);

        List<FasePlantillaChecklist> fasesOrigen = fases.findAllByPlantillaIdOrderByOrdenAscCodigoAsc(id);
        Map<UUID, UUID> faseNueva = new HashMap<>();
        for (FasePlantillaChecklist f : fasesOrigen) {
            FasePlantillaChecklist nueva = new FasePlantillaChecklist();
            nueva.setPlantillaId(copia.getId());
            nueva.setCodigo(f.getCodigo());
            nueva.setNombre(f.getNombre());
            nueva.setDescripcion(f.getDescripcion());
            nueva.setOrden(f.getOrden());
            faseNueva.put(f.getId(), fases.save(nueva).getId());
        }
        for (ItemPlantillaChecklist i : items.findAllByFaseIdInOrderByOrdenAscCodigoAsc(ids(fasesOrigen))) {
            ItemPlantillaChecklist nuevo = new ItemPlantillaChecklist();
            nuevo.setFaseId(faseNueva.get(i.getFaseId()));
            nuevo.setCodigo(i.getCodigo());
            nuevo.setEtiqueta(i.getEtiqueta());
            nuevo.setDescripcion(i.getDescripcion());
            nuevo.setTipoRespuesta(i.getTipoRespuesta());
            nuevo.setObligatorio(i.isObligatorio());
            nuevo.setBloqueante(i.isBloqueante());
            nuevo.setRolClinicoResponsableId(i.getRolClinicoResponsableId());
            nuevo.setConfigValidacion(i.getConfigValidacion());
            nuevo.setOrden(i.getOrden());
            items.save(nuevo);
        }
        items.flush();
        auditoria.registrar(TABLA, copia.getId(), AccionAuditoria.NUEVA_VERSION, PlantillaResponse.de(origen),
                PlantillaResponse.de(copia));
        return detalle(copia);
    }

    // ---------------------------------------------------------------- fases

    @Transactional
    public FasePlantillaResponse crearFase(UUID plantillaId, FasePlantillaRequest datos) {
        buscarPlantilla(plantillaId);
        FasePlantillaChecklist fase = new FasePlantillaChecklist();
        fase.setPlantillaId(plantillaId);
        aplicar(fase, datos);
        FasePlantillaResponse creada = FasePlantillaResponse.de(fases.saveAndFlush(fase), List.of());
        auditoria.registrar(TABLA_FASES, fase.getId(), AccionAuditoria.CREAR, null, creada);
        return creada;
    }

    @Transactional
    public FasePlantillaResponse actualizarFase(UUID plantillaId, UUID faseId, FasePlantillaRequest datos) {
        FasePlantillaChecklist fase = buscarFase(plantillaId, faseId);
        FasePlantillaResponse anterior = FasePlantillaResponse.de(fase, List.of());
        aplicar(fase, datos);
        fases.saveAndFlush(fase);
        auditoria.registrar(TABLA_FASES, faseId, AccionAuditoria.ACTUALIZAR, anterior,
                FasePlantillaResponse.de(fase, List.of()));
        return FasePlantillaResponse.de(fase, itemsDe(List.of(fase)).getOrDefault(fase.getId(), List.of()));
    }

    @Transactional
    public void eliminarFase(UUID plantillaId, UUID faseId) {
        FasePlantillaChecklist fase = buscarFase(plantillaId, faseId);
        FasePlantillaResponse anterior = FasePlantillaResponse.de(fase,
                itemsDe(List.of(fase)).getOrDefault(faseId, List.of()));
        items.deleteAllInBatch(items.findAllByFaseIdOrderByOrdenAscCodigoAsc(faseId));
        fases.delete(fase);
        fases.flush();
        auditoria.registrar(TABLA_FASES, faseId, AccionAuditoria.ELIMINAR, anterior, null);
    }

    // ---------------------------------------------------------------- ítems

    @Transactional
    public ItemPlantillaResponse crearItem(UUID plantillaId, UUID faseId, ItemPlantillaRequest datos) {
        buscarFase(plantillaId, faseId);
        ItemPlantillaChecklist item = new ItemPlantillaChecklist();
        item.setFaseId(faseId);
        aplicar(item, datos);
        ItemPlantillaResponse creado = respuesta(items.saveAndFlush(item));
        auditoria.registrar(TABLA_ITEMS, item.getId(), AccionAuditoria.CREAR, null, creado);
        return creado;
    }

    @Transactional
    public ItemPlantillaResponse actualizarItem(UUID plantillaId, UUID faseId, UUID itemId,
            ItemPlantillaRequest datos) {
        ItemPlantillaChecklist item = buscarItem(plantillaId, faseId, itemId);
        ItemPlantillaResponse anterior = respuesta(item);
        aplicar(item, datos);
        ItemPlantillaResponse actualizado = respuesta(items.saveAndFlush(item));
        auditoria.registrar(TABLA_ITEMS, itemId, AccionAuditoria.ACTUALIZAR, anterior, actualizado);
        return actualizado;
    }

    @Transactional
    public void eliminarItem(UUID plantillaId, UUID faseId, UUID itemId) {
        ItemPlantillaChecklist item = buscarItem(plantillaId, faseId, itemId);
        ItemPlantillaResponse anterior = respuesta(item);
        items.delete(item);
        items.flush();
        auditoria.registrar(TABLA_ITEMS, itemId, AccionAuditoria.ELIMINAR, anterior, null);
    }

    // ---------------------------------------------------------------- apoyo

    private PlantillaChecklist buscarPlantilla(UUID id) {
        return plantillas.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Plantilla de checklist no encontrada: " + id));
    }

    private FasePlantillaChecklist buscarFase(UUID plantillaId, UUID faseId) {
        buscarPlantilla(plantillaId);
        return fases.findByIdAndPlantillaId(faseId, plantillaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La fase " + faseId + " no existe en la plantilla " + plantillaId));
    }

    private ItemPlantillaChecklist buscarItem(UUID plantillaId, UUID faseId, UUID itemId) {
        buscarFase(plantillaId, faseId);
        return items.findByIdAndFaseId(itemId, faseId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El ítem " + itemId + " no existe en la fase " + faseId));
    }

    private PlantillaDetalleResponse detalle(PlantillaChecklist plantilla) {
        List<FasePlantillaChecklist> suyas = fases.findAllByPlantillaIdOrderByOrdenAscCodigoAsc(plantilla.getId());
        Map<UUID, List<ItemPlantillaResponse>> porFase = itemsDe(suyas);
        return PlantillaDetalleResponse.de(plantilla, suyas.stream()
                .map(f -> FasePlantillaResponse.de(f, porFase.getOrDefault(f.getId(), List.of())))
                .toList());
    }

    /** Ítems de las fases agrupados por fase, en orden, con el rol responsable resuelto (2 consultas). */
    private Map<UUID, List<ItemPlantillaResponse>> itemsDe(List<FasePlantillaChecklist> deFases) {
        List<ItemPlantillaChecklist> filas = items.findAllByFaseIdInOrderByOrdenAscCodigoAsc(ids(deFases));
        Map<UUID, RolClinicoVista> rolPorId = roles.findAllById(filas.stream()
                        .map(ItemPlantillaChecklist::getRolClinicoResponsableId)
                        .filter(r -> r != null)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(RolClinico::getId, r -> new RolClinicoVista(r.getCodigo(), r.getNombre())));
        Map<UUID, List<ItemPlantillaResponse>> porFase = new LinkedHashMap<>();
        for (ItemPlantillaChecklist i : filas) {
            porFase.computeIfAbsent(i.getFaseId(), k -> new ArrayList<>())
                    .add(ItemPlantillaResponse.de(i, rolPorId.get(i.getRolClinicoResponsableId())));
        }
        return porFase;
    }

    private ItemPlantillaResponse respuesta(ItemPlantillaChecklist item) {
        RolClinicoVista rol = item.getRolClinicoResponsableId() == null ? null
                : roles.findById(item.getRolClinicoResponsableId())
                        .map(r -> new RolClinicoVista(r.getCodigo(), r.getNombre()))
                        .orElse(null);
        return ItemPlantillaResponse.de(item, rol);
    }

    private static List<UUID> ids(List<FasePlantillaChecklist> deFases) {
        return deFases.stream().map(FasePlantillaChecklist::getId).toList();
    }

    private static void aplicar(FasePlantillaChecklist fase, FasePlantillaRequest datos) {
        fase.setCodigo(Textos.codigo(datos.codigo()));
        fase.setNombre(Textos.limpiar(datos.nombre()));
        fase.setDescripcion(Textos.limpiar(datos.descripcion()));
        fase.setOrden(datos.orden());
    }

    private static void aplicar(ItemPlantillaChecklist item, ItemPlantillaRequest datos) {
        item.setCodigo(Textos.codigo(datos.codigo()));
        item.setEtiqueta(Textos.limpiar(datos.etiqueta()));
        item.setDescripcion(Textos.limpiar(datos.descripcion()));
        item.setTipoRespuesta(Textos.codigo(datos.tipoRespuesta()));
        item.setObligatorio(datos.obligatorio() == null || datos.obligatorio());
        item.setBloqueante(datos.bloqueante() != null && datos.bloqueante());
        item.setRolClinicoResponsableId(datos.rolClinicoResponsableId());
        item.setConfigValidacion(datos.configValidacion() == null || datos.configValidacion().isNull() ? null
                : datos.configValidacion().toString());
        item.setOrden(datos.orden());
    }
}
