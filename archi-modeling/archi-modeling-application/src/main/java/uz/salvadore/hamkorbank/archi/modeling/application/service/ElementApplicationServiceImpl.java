package uz.salvadore.hamkorbank.archi.modeling.application.service;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.DeleteElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ElementCreateCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ElementDeleteCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ElementEditCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ElementApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Элементы модели: делегирует в handler'ы сценариев. */
public final class ElementApplicationServiceImpl implements ElementApplicationService {

    private final ElementCreateCommandHandler create;
    private final ElementEditCommandHandler edit;
    private final ElementDeleteCommandHandler delete;

    public ElementApplicationServiceImpl(ElementCreateCommandHandler create, ElementEditCommandHandler edit,
                                         ElementDeleteCommandHandler delete) {
        this.create = create;
        this.edit = edit;
        this.delete = delete;
    }

    @Override
    public ElementResponse createElement(EditorIdentity actor, CreateElementCommand command) {
        return create.handle(actor, command);
    }

    @Override
    public ElementResponse editElement(EditorIdentity actor, EditElementCommand command) {
        return edit.handle(actor, command);
    }

    @Override
    public void deleteElement(EditorIdentity actor, DeleteElementCommand command) {
        delete.handle(actor, command);
    }
}
