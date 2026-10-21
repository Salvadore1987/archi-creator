package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.List;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;

/** История версий. Список отдаётся без снимков: сотня версий по 150 КБ в список не нужна. */
public interface ModelVersionRepository {

    /** Версия модели по номеру, со снимком, если он ещё не очищен. */
    Optional<ModelVersion> find(ModelId modelId, long versionNo);

    /** Последняя версия модели; у модели без версий пусто. */
    Optional<ModelVersion> last(ModelId modelId);

    /** Версии без снимков, от новых к старым; наличие снимка — {@link ModelVersion#hasSnapshot()}. */
    List<ModelVersion> list(ModelId modelId);

    /** Страница того же списка: версии с номером меньше {@code before}, не больше {@code size}. */
    List<ModelVersion> page(ModelId modelId, long before, int size);

    /** Вставка новой версии или правка метки и очистка снимка у существующей. */
    void save(ModelVersion version);

    /** Модели, у которых есть хотя бы один снимок, — обход фоновой очистки. */
    List<ModelId> modelsWithSnapshots();
}
