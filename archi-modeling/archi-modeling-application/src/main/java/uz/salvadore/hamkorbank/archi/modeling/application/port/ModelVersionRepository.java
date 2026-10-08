package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.List;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;

/** История версий. Список отдаётся без снимков: сотня версий по 150 КБ в список не нужна. */
public interface ModelVersionRepository {

    Optional<ModelVersion> find(ModelId modelId, long versionNo);

    Optional<ModelVersion> last(ModelId modelId);

    /** Версии без снимков, от новых к старым; наличие снимка — в {@code snapshot().isPresent()}. */
    List<ModelVersion> list(ModelId modelId);

    /** Вставка новой версии или правка метки и очистка снимка у существующей. */
    void save(ModelVersion version);

    /** Модели, у которых есть хотя бы один снимок, — обход фоновой очистки. */
    List<ModelId> modelsWithSnapshots();
}
