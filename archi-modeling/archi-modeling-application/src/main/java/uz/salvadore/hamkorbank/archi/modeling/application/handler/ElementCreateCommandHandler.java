package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ModelDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotentCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiTypeRegistry;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** Новый элемент в папке своего слоя; повтор с тем же ключом — тот же элемент. */
public final class ElementCreateCommandHandler {

    private final ModelingKernel kernel;

    public ElementCreateCommandHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Элемент с идентификаторами клиента, если он их выбрал; занятые — {@code 409}. */
    public ElementResponse handle(EditorIdentity actor, CreateElementCommand command) {
        return kernel.run(Operation.CREATE_ELEMENT, actor, () -> {
            ArchiType archiType = RequestValues.archiType(command.archiType());
            ModelId modelId = ModelId.of(command.modelId());
            FolderId folderId = command.folderId() == null ? null : FolderId.of(command.folderId());
            RequestedIds ids = RequestedIds.of(command.id(), command.archiId());
            return kernel.unitOfWork().write(() -> kernel.idempotent("CreateElement", actor,
                    command.idempotencyKey(),
                    IdempotentCommand.fingerprint(modelId, archiType.value(), command.name(), folderId, ids.id(),
                            ids.archiId()),
                    () -> {
                        ArchitectureModel model = kernel.writableModel(modelId, actor);
                        FolderId folder = folderId != null ? folderId : defaultFolder(model, archiType);
                        Element element = model.addElement(archiType, command.name(), folder,
                                ElementId.of(kernel.newId(ids)), kernel.newArchiId(model, ids), kernel.now());
                        kernel.save(model);
                        return element.id().toString();
                    },
                    ref -> ModelDataMapper.element(
                            kernel.models().load(modelId).orElseThrow().requireElement(ElementId.of(ref)))));
        });
    }

    private static FolderId defaultFolder(ArchitectureModel model, ArchiType archiType) {
        return model.root(FolderType.forLayer(ArchiTypeRegistry.archimate32().layerOf(archiType))).id();
    }
}
