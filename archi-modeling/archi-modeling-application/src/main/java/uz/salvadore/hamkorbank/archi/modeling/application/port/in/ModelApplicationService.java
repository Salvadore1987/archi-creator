package uz.salvadore.hamkorbank.archi.modeling.application.port.in;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.DeleteModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.PurgeModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RestoreModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Жизненный цикл модели: создать, переименовать, удалить, восстановить, уничтожить. */
public interface ModelApplicationService {

    /** Пустая модель с версией 1; повтор ключа — та же модель. */
    ModelSummaryResponse createModel(EditorIdentity actor, CreateModelCommand command);

    /** Имя и описание; нужна блокировка автора. */
    ModelSummaryResponse editModel(EditorIdentity actor, EditModelCommand command);

    /** Мягкое удаление под своей блокировкой. */
    void deleteModel(EditorIdentity actor, DeleteModelCommand command);

    /** Возврат удалённой модели в работу; только {@code ADMIN}. */
    ModelSummaryResponse restoreModel(EditorIdentity actor, RestoreModelCommand command);

    /** Физическое уничтожение удалённой модели; только {@code ADMIN}. */
    void purgeModel(EditorIdentity actor, PurgeModelCommand command);
}
