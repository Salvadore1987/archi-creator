package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.Collection;
import java.util.Map;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.ModelAccessList;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** Списки доступа (INV-MDL-011). Нет записей — список пуст, модель открыта по ролям. */
public interface ModelAccessListRepository {

    ModelAccessList find(ModelId modelId);

    Map<ModelId, ModelAccessList> findAll(Collection<ModelId> modelIds);

    void save(ModelAccessList list);
}
