package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.List;
import java.util.Optional;
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

    Optional<ArchitectureModel> load(ModelId id);

    Optional<ModelHeader> findHeader(ModelId id);

    /** Модели рабочего пространства, кроме физически удалённых. */
    List<ModelHeader> list(WorkspaceId workspaceId);

    /**
     * Запись изменений агрегата. Свежий — вставка целиком; загруженный — заголовок
     * с проверкой версии и тронутые сущности.
     *
     * @throws ConcurrentModificationException строку модели успел изменить другой писатель
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
    Optional<ModelId> ownerOf(java.util.UUID objectId);

    /**
     * Занят ли ключ хоть одной строкой содержимого — папкой, элементом, связью,
     * представлением, узлом или ребром любой модели.
     */
    boolean idTaken(java.util.UUID id);

    /** Занят ли {@code archiId} узлом или ребром какого-нибудь представления модели. */
    boolean diagramArchiIdTaken(ModelId modelId, uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId archiId);

    /** Где элемент или связь вообще упоминаются на представлениях — чтобы снять размещения. */
    List<ViewId> viewsReferencing(ElementId elementId);

    List<ViewId> viewsReferencing(RelationshipId relationshipId);
}
