package uz.salvadore.hamkorbank.archi.interchange.application.port.in;

import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.ConfigureGitBindingCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.DisableGitBindingCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.GetGitBindingQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitBindingResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Привязка рабочего пространства к Git-репозиторию — только {@code ADMIN}. */
public interface GitBindingApplicationService {

    /** Текущая привязка; нет привязки — «не найдено». */
    GitBindingResponse getGitBinding(EditorIdentity actor, GetGitBindingQuery query);

    /** Задаёт привязку заново: адрес, ветку, ссылку на токен и авторов. */
    GitBindingResponse configureGitBinding(EditorIdentity actor, ConfigureGitBindingCommand command);

    /** Снимает привязку; коммиты в репозитории остаются. */
    void disableGitBinding(EditorIdentity actor, DisableGitBindingCommand command);
}
