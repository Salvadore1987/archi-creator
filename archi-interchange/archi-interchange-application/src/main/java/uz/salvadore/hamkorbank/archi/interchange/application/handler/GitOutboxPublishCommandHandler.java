package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import uz.salvadore.hamkorbank.archi.interchange.application.git.GitPublisher;

/**
 * Проход очереди публикации — системный вход планировщика, без актора: роли у него нет,
 * версии в очередь положили те, у кого она была.
 */
public final class GitOutboxPublishCommandHandler {

    private final GitPublisher publisher;

    public GitOutboxPublishCommandHandler(GitPublisher publisher) {
        this.publisher = publisher;
    }

    /** Сколько записей очереди закрыто. */
    public int handle() {
        return publisher.publishPending();
    }
}
