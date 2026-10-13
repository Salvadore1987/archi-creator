package uz.salvadore.hamkorbank.archi.interchange.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/**
 * Очередь версий на публикацию — записи {@code ModelVersionCommitted} в outbox.
 * Пишется той же транзакцией, что версия; читается по порядку версий внутри модели.
 */
public interface GitOutbox {

    /** В транзакции вызывающего: откат сохранения не оставляет записи. */
    void enqueue(WorkspaceId workspace, ModelId model, long versionNo, Instant at);

    /** Голова очереди каждой модели — самая ранняя незакрытая запись. */
    List<Entry> heads();

    Optional<Entry> head(ModelId model);

    /** Сколько версий модели ещё не опубликовано. */
    int open(ModelId model);

    void done(UUID id, Instant at);

    /** Публиковать нечего или некуда: модели нет, привязки нет. */
    void skipped(UUID id, Instant at);

    void diverged(UUID id, String reason, Instant at);

    void retryLater(UUID id, String reason, Instant next);

    enum Status { PENDING, DIVERGED }

    record Entry(UUID id, WorkspaceId workspace, ModelId model, long versionNo, Status status, int attempts,
                 Optional<Instant> nextAttemptAt) {
    }
}
