package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.List;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;

/** Хранилище представлений: payload открывается по требованию, сборка — одним запросом. */
public interface ViewRepository {

    /** Представление с узлами и рёбрами. */
    Optional<View> load(ViewId id);

    /** Все представления модели — для снимка версии и экспорта. */
    List<View> loadAll(ModelId modelId);

    /** Представление, которому принадлежит узел. */
    Optional<ViewId> viewOfNode(ViewNodeId nodeId);

    /** Представление, которому принадлежит ребро. */
    Optional<ViewId> viewOfEdge(ViewEdgeId edgeId);

    /**
     * Размещения элементов и связей на представлениях модели — без загрузки самих
     * представлений, числом запросов, не зависящим от размера модели. Представления без
     * единого узла над элементом и ребра связи в ответ могут не попасть.
     */
    List<ViewPlacements> placements(ModelId modelId);

    /** Записывает представление с узлами и рёбрами. */
    void save(View view);

    /** Удаляет представление вместе с узлами и рёбрами. */
    void delete(ViewId id);
}
