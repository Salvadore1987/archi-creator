package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotentCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelFolder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Relationship;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ViewRef;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;

/**
 * UC-MDL-006: папки, перенос, переименование и удаление в дереве (FR-34, FR-36). Групповая
 * операция — одна транзакция: часть не проходит — не применяется ничего.
 */
public final class TreeService {

    private final ModelingKernel kernel;
    private final ElementService elements;
    private final RelationshipService relationships;

    public TreeService(ModelingKernel kernel, ElementService elements, RelationshipService relationships) {
        this.kernel = kernel;
        this.elements = elements;
        this.relationships = relationships;
    }

    public ModelFolder createFolder(EditorIdentity actor, ModelId modelId, FolderId parentId, String name,
                                    Optional<String> idempotencyKey) {
        return kernel.run(Operation.REORGANIZE_TREE, actor, () -> kernel.unitOfWork.write(() ->
                kernel.idempotent("CreateFolder", actor, idempotencyKey,
                        IdempotentCommand.fingerprint(modelId, parentId, name),
                        () -> {
                            ArchitectureModel model = kernel.writableModel(modelId, actor);
                            ModelFolder folder = model.createFolder(parentId, name, FolderId.next(kernel.uuids),
                                    kernel.newArchiId(model), kernel.now());
                            kernel.save(model);
                            return folder.id().toString();
                        },
                        ref -> kernel.models.load(modelId).orElseThrow().requireFolder(FolderId.of(ref)))));
    }

    /** Переименование папки, элемента, связи или представления. */
    public void rename(EditorIdentity actor, ModelId modelId, UUID itemId, String name) {
        kernel.run(Operation.REORGANIZE_TREE, actor, () -> kernel.unitOfWork.write(() -> {
            ArchitectureModel model = kernel.writableModel(modelId, actor);
            if (model.folders().contains(FolderId.of(itemId))) {
                model.renameFolder(FolderId.of(itemId), name, kernel.now());
            } else if (model.elements().contains(ElementId.of(itemId))) {
                Element element = model.requireElement(ElementId.of(itemId));
                model.updateElement(element.id(), name, element.documentation(), element.properties(), kernel.now());
            } else if (model.relationships().contains(RelationshipId.of(itemId))) {
                Relationship relationship = model.requireRelationship(RelationshipId.of(itemId));
                model.updateRelationship(relationship.id(), Optional.of(name), relationship.documentation(),
                        relationship.properties(), kernel.now());
            } else if (model.view(ViewId.of(itemId)).isPresent()) {
                View view = kernel.views.load(ViewId.of(itemId)).orElseThrow();
                view.rename(name);
                model.renameView(view.id(), name);
                model.touch(kernel.now());
                kernel.views.save(view);
            } else {
                throw ModelingException.notFound("объект " + itemId + " в модели");
            }
            kernel.save(model);
            return null;
        }));
    }

    /** Перенос в папку; соседи не перенумеровываются (INV-MDL-005), корни не меняются (INV-MDL-009). */
    public void move(EditorIdentity actor, ModelId modelId, FolderId targetId, List<UUID> itemIds) {
        kernel.run(Operation.REORGANIZE_TREE, actor, () -> kernel.unitOfWork.write(() -> {
            ArchitectureModel model = kernel.writableModel(modelId, actor);
            for (ViewRef moved : model.moveToFolder(targetId, itemIds, kernel.now())) {
                View view = kernel.views.load(moved.id()).orElseThrow();
                view.moveTo(moved);
                kernel.views.save(view);
            }
            kernel.save(model);
            return null;
        }));
    }

    /**
     * Групповое удаление. Порядок — связи, представления, элементы, папки: так удаление
     * элемента вместе с его связями проходит одной командой, а связь, оставшаяся вне
     * команды, даёт {@code 409} с перечнем (INV-MDL-004).
     */
    public void delete(EditorIdentity actor, ModelId modelId, List<UUID> itemIds) {
        kernel.run(Operation.REORGANIZE_TREE, actor, () -> kernel.unitOfWork.write(() -> {
            ArchitectureModel model = kernel.writableModel(modelId, actor);
            List<UUID> folders = new ArrayList<>();
            List<UUID> elementIds = new ArrayList<>();
            for (UUID id : itemIds) {
                if (model.relationships().contains(RelationshipId.of(id))) {
                    relationships.deleteIn(model, RelationshipId.of(id));
                } else if (model.view(ViewId.of(id)).isPresent()) {
                    model.forgetView(ViewId.of(id), kernel.now());
                    kernel.views.delete(ViewId.of(id));
                } else if (model.elements().contains(ElementId.of(id))) {
                    elementIds.add(id);
                } else if (model.folders().contains(FolderId.of(id))) {
                    folders.add(id);
                } else {
                    throw ModelingException.notFound("объект " + id + " в модели");
                }
            }
            elementIds.forEach(id -> elements.deleteIn(model, ElementId.of(id)));
            folders.forEach(id -> model.removeFolder(FolderId.of(id), kernel.now()));
            kernel.save(model);
            return null;
        }));
    }
}
