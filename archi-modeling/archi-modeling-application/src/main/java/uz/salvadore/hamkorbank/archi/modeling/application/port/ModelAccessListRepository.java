package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.Collection;
import java.util.Map;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.ModelAccessList;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** Списки доступа модели. Нет записей — список пуст, модель открыта по ролям. */
public interface ModelAccessListRepository {

    /** Список доступа модели; у модели без записей — пустой. */
    ModelAccessList find(ModelId modelId);

    /** Списки доступа нескольких моделей одним запросом — для фильтрации перечня моделей. */
    Map<ModelId, ModelAccessList> findAll(Collection<ModelId> modelIds);

    /** Заменяет список доступа модели целиком. */
    void save(ModelAccessList list);
}
