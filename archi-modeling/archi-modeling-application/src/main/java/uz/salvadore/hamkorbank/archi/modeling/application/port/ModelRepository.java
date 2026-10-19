package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Хранилище агрегата модели. Дерево — папки, элементы, связи, места представлений —
 * читается целиком; пишется только изменённое.
 */
public interface ModelRepository {

    /** Агрегат модели целиком: заголовок, дерево папок, элементы, связи, места представлений. */
    Optional<ArchitectureModel> load(ModelId id);

    /** Только заголовок модели — для проверки доступа и статуса без загрузки дерева. */
    Optional<ModelHeader> findHeader(ModelId id);

    /** Модели рабочего пространства, кроме физически удалённых. */
    List<ModelHeader> list(WorkspaceId workspaceId);

    /**
     * Запись изменений агрегата. Свежий — вставка целиком; загруженный — заголовок
     * с проверкой версии и тронутые сущности.
     *
     * @throws ConcurrentUpdateException строку модели успел изменить другой писатель
     */
    void save(ArchitectureModel model);

    /**
     * Содержимое модели заменяется целиком — откат к версии. Заголовок проверяется
     * по версии строки, папки, элементы, связи и представления пишутся заново.
     */
    void replaceContent(ModelContent content);

    /** Модель целиком, с представлениями и версиями: физическое удаление (PurgeModel). */
    void purge(ModelId id);

    /** Модель, которой принадлежит папка, элемент, связь или представление с этим ключом. */
    Optional<ModelId> ownerOf(UUID objectId);

    /**
     * Занят ли ключ хоть одной строкой содержимого — папкой, элементом, связью,
     * представлением, узлом или ребром любой модели.
     */
    boolean idTaken(UUID id);

    /** Занят ли {@code archiId} узлом или ребром какого-нибудь представления модели. */
    boolean diagramArchiIdTaken(ModelId modelId, ArchiId archiId);

    /** Где элемент или связь вообще упоминаются на представлениях — чтобы снять размещения. */
    List<ViewId> viewsReferencing(ElementId elementId);

    /** Представления, на которых связь нарисована ребром. */
    List<ViewId> viewsReferencing(RelationshipId relationshipId);
}
