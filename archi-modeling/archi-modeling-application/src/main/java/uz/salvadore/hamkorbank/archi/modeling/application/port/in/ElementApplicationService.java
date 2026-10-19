package uz.salvadore.hamkorbank.archi.modeling.application.port.in;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.DeleteElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Элементы модели: создать, изменить, удалить. */
public interface ElementApplicationService {

    /** Новый элемент в папке своего слоя; повтор ключа — тот же элемент. */
    ElementResponse createElement(EditorIdentity actor, CreateElementCommand command);

    /** Имя, описание и свойства; незаданное поле не меняется. */
    ElementResponse editElement(EditorIdentity actor, EditElementCommand command);

    /** Удаление; со связями — {@code 409}. */
    void deleteElement(EditorIdentity actor, DeleteElementCommand command);
}
