package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.List;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;

/** Хранилище представлений: payload открывается по требованию, сборка — одним запросом. */
public interface ViewRepository {

    Optional<View> load(ViewId id);

    /** Все представления модели — для снимка версии и экспорта. */
    List<View> loadAll(ModelId modelId);

    Optional<ViewId> viewOfNode(ViewNodeId nodeId);

    void save(View view);

    void delete(ViewId id);
}
