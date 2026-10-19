package uz.salvadore.hamkorbank.archi.interchange.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/**
 * Очередь версий на публикацию — записи {@code ModelVersionCommittedEvent} в outbox.
 * Пишется той же транзакцией, что версия; читается по порядку версий внутри модели.
 */
public interface GitOutbox {

    /** В транзакции вызывающего: откат сохранения не оставляет записи. */
    void enqueue(WorkspaceId workspace, ModelId model, long versionNo, Instant at);

    /** Голова очереди каждой модели — самая ранняя незакрытая запись. */
    List<Entry> heads();

    /** Самая ранняя незакрытая запись модели. */
    Optional<Entry> head(ModelId model);

    /** Сколько версий модели ещё не опубликовано. */
    int open(ModelId model);

    /** Версия опубликована: запись закрывается. */
    void done(UUID id, Instant at);

    /** Публиковать нечего или некуда: модели нет, привязки нет. */
    void skipped(UUID id, Instant at);

    /** Публикация встала: ветка разошлась с нашей историей. */
    void diverged(UUID id, String reason, Instant at);

    /**
     * Открытые записи модели по версию {@code throughVersionNo} включительно закрываются
     * как пропущенные — их место в ветке заняло принятое состояние. В транзакции вызывающего.
     */
    void supersede(ModelId model, long throughVersionNo, Instant at);

    /** Сбой сети или сервера: запись повторится не раньше {@code next}. */
    void retryLater(UUID id, String reason, Instant next);

    /** Состояние открытой записи: ждёт публикации или встала на расхождении. */
    enum Status { PENDING, DIVERGED }

    /**
     * Открытая запись очереди: версия модели, число попыток и время следующей.
     *
     * @param nextAttemptAt время следующей попытки; {@code null} — не назначено
     */
    record Entry(UUID id, WorkspaceId workspace, ModelId model, long versionNo, Status status, int attempts,
                 Instant nextAttemptAt) {
    }
}
