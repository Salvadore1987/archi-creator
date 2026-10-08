package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotentCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;

/** Элементы модели: создать, изменить, удалить. */
public final class ElementService {

    private final ModelingKernel kernel;

    public ElementService(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Свойство в правке: ключ и значение, порядок — по месту в списке. */
    public record PropertyValue(String key, String value) {
    }

    /** Новый элемент в папке своего слоя; повтор с тем же ключом — тот же элемент. */
    public Element create(EditorIdentity actor, ModelId modelId, ArchiType archiType, String name,
                          Optional<FolderId> folderId, Optional<String> idempotencyKey) {
        return kernel.run(Operation.CREATE_ELEMENT, actor, () -> kernel.unitOfWork.write(() ->
                kernel.idempotent("CreateElement", actor, idempotencyKey,
                        IdempotentCommand.fingerprint(modelId, archiType, name, folderId),
                        () -> {
                            ArchitectureModel model = kernel.writableModel(modelId, actor);
                            FolderId folder = folderId.orElseGet(() -> defaultFolder(model, archiType));
                            Element element = model.addElement(archiType, name, folder, ElementId.next(kernel.uuids),
                                    kernel.newArchiId(model), kernel.now());
                            kernel.save(model);
                            return element.id().toString();
                        },
                        ref -> kernel.models.load(modelId).orElseThrow().requireElement(ElementId.of(ref)))));
    }

    /**
     * PATCH элемента. Переименование видно на всех представлениях сразу: узел ссылается
     * на элемент, а не копирует его имя.
     */
    public Element update(EditorIdentity actor, ElementId elementId, Optional<String> name,
                          Optional<String> documentation, Optional<List<PropertyValue>> properties) {
        return kernel.run(Operation.UPDATE_ELEMENT, actor, () -> kernel.unitOfWork.write(() -> {
            ArchitectureModel model = kernel.writableModel(ownerOf(elementId.value()), actor);
            Element current = model.requireElement(elementId);
            Element updated = model.updateElement(elementId, name.orElse(current.name()),
                    documentation.isPresent() ? documentation : current.documentation(),
                    properties.map(ElementService::numbered).orElse(current.properties()), kernel.now());
            kernel.save(model);
            return updated;
        }));
    }

    /** Удаление; со связями — {@code 409}. Размещения на представлениях уходят вместе с ним. */
    public void delete(EditorIdentity actor, ElementId elementId) {
        kernel.run(Operation.DELETE_ELEMENT, actor, () -> kernel.unitOfWork.write(() -> {
            ArchitectureModel model = kernel.writableModel(ownerOf(elementId.value()), actor);
            deleteIn(model, elementId);
            kernel.save(model);
            return null;
        }));
    }

    void deleteIn(ArchitectureModel model, ElementId elementId) {
        model.removeElement(elementId, kernel.now());
        for (ViewId viewId : kernel.models.viewsReferencing(elementId)) {
            View view = kernel.views.load(viewId).orElseThrow();
            if (view.removeElementReferences(elementId)) {
                kernel.views.save(view);
            }
        }
    }

    ModelId ownerOf(UUID objectId) {
        return kernel.models.ownerOf(objectId).orElseThrow(() -> ModelingException.notFound("объект " + objectId));
    }

    /** Свойства получают разреженный порядок по месту в списке. */
    static List<PropertyEntry> numbered(List<PropertyValue> values) {
        java.util.ArrayList<PropertyEntry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            entries.add(new PropertyEntry(values.get(i).key(), values.get(i).value(), SortOrder.ofPosition(i)));
        }
        return entries;
    }

    private static FolderId defaultFolder(ArchitectureModel model, ArchiType archiType) {
        var layer = uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiTypeRegistry.archimate32().layerOf(archiType);
        return model.roots().get(uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderType.forLayer(layer)).id();
    }
}
