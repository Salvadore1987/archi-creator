package uz.salvadore.hamkorbank.archi.interchange.application.port.in;

import java.util.Optional;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.PushModelToGitCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.RestoreGitSnapshotQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ModelGitStatusResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/**
 * Публикация зафиксированных версий в Git. Ручная публикация — сценарий с ролью; проход очереди,
 * проверка доступности и восстановление снимка — системные входы планировщика и modeling: роль
 * у них проверяет вызывающий.
 */
public interface GitPublishingApplicationService {

    /** Опубликовать ожидающие версии модели сейчас; ответ — состояние модели в Git. */
    ModelGitStatusResponse pushModelToGit(EditorIdentity actor, PushModelToGitCommand command);

    /** Проход очереди публикации; ответ — сколько записей закрыто. */
    int publishPendingVersions();

    /** Настроен ли и отвечает ли репозиторий пространства — живой проверкой с записью исхода. */
    boolean repositoryReachable(UUID workspaceId);

    /** Снимок версии из её коммита; пусто — привязки нет или каталога модели в коммите нет. */
    Optional<byte[]> restoreSnapshot(RestoreGitSnapshotQuery query);
}
