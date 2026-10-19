package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.lock.ModelLock;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** Не более одной блокировки на модель: первичный ключ — {@code model_id}. */
public interface ModelLockRepository {

    /** Записанная блокировка модели, если она есть; срок действия проверяет вызывающий. */
    Optional<ModelLock> find(ModelId modelId);

    /** Вставка или замена; параллельный захват той же модели — {@link ConcurrentUpdateException}. */
    void save(ModelLock lock);

    /** Снимает блокировку модели. */
    void delete(ModelId modelId);
}
