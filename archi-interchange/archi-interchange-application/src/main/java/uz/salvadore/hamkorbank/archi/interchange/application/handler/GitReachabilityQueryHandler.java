package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import java.util.UUID;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitPublisher;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/**
 * Настроен ли и доступен ли репозиторий пространства — живой проверкой. Вход modeling для очистки
 * снимков: роль у этой операции проверяет вызывающий.
 */
public final class GitReachabilityQueryHandler {

    private final GitPublisher publisher;

    public GitReachabilityQueryHandler(GitPublisher publisher) {
        this.publisher = publisher;
    }

    /** Ложь — привязки нет или репозиторий не ответил. */
    public boolean handle(UUID workspaceId) {
        return publisher.reachable(new WorkspaceId(workspaceId));
    }
}
