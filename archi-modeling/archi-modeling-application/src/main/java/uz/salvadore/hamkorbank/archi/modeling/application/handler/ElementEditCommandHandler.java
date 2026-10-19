package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ModelDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;

/**
 * Правка элемента. Переименование видно на всех представлениях сразу: узел ссылается
 * на элемент, а не копирует его имя.
 */
public final class ElementEditCommandHandler {

    private final ModelingKernel kernel;
    private final ModelContents contents;

    public ElementEditCommandHandler(ModelingKernel kernel, ModelContents contents) {
        this.kernel = kernel;
        this.contents = contents;
    }

    /** Заданные поля меняются, незаданные остаются; нужна блокировка модели. */
    public ElementResponse handle(EditorIdentity actor, EditElementCommand command) {
        return kernel.run(Operation.UPDATE_ELEMENT, actor, () -> kernel.unitOfWork().write(() -> {
            ElementId elementId = ElementId.of(command.elementId());
            ArchitectureModel model = kernel.writableModel(contents.ownerOf(command.elementId()), actor);
            Element current = model.requireElement(elementId);
            Element updated = model.updateElement(elementId,
                    command.name() != null ? command.name() : current.name(),
                    command.documentation() != null ? command.documentation() : current.documentation(),
                    command.properties() != null ? ModelContents.numbered(command.properties()) : current.properties(),
                    kernel.now());
            kernel.save(model);
            return ModelDataMapper.element(updated);
        }));
    }
}
