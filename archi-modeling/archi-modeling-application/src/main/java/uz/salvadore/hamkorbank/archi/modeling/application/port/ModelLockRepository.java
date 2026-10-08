package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.lock.ModelLock;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** Не более одной блокировки на модель: первичный ключ — {@code model_id} (INV-MDL-006). */
public interface ModelLockRepository {

    Optional<ModelLock> find(ModelId modelId);

    /** Вставка или замена; параллельный захват той же модели — {@link ConcurrentModificationException}. */
    void save(ModelLock lock);

    void delete(ModelId modelId);
}
