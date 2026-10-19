package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.DeleteElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;

/** Удаление элемента; со связями — {@code 409}. Размещения на представлениях уходят вместе с ним. */
public final class ElementDeleteCommandHandler {

    private final ModelingKernel kernel;
    private final ModelContents contents;

    public ElementDeleteCommandHandler(ModelingKernel kernel, ModelContents contents) {
        this.kernel = kernel;
        this.contents = contents;
    }

    /** Элемент удаляется под блокировкой модели. */
    public void handle(EditorIdentity actor, DeleteElementCommand command) {
        kernel.run(Operation.DELETE_ELEMENT, actor, () -> kernel.unitOfWork().write(() -> {
            ArchitectureModel model = kernel.writableModel(contents.ownerOf(command.elementId()), actor);
            contents.removeElement(model, ElementId.of(command.elementId()));
            kernel.save(model);
            return null;
        }));
    }
}
