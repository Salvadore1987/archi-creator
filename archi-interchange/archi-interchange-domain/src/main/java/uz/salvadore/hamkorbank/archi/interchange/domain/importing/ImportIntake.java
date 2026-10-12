package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ImportSessionId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.UuidV7;

/**
 * Приём файла на импорт с учётом идемпотентности.
 *
 * <p>Тот же ключ и тот же файл — существующая сессия и её результат; новая модель не
 * создаётся, новая версия не пишется. Тот же ключ с другим отпечатком — конфликт.
 * Ретрай прокси на трёхсекундном импорте иначе создал бы модель-двойник.
 */
public final class ImportIntake {

    private final ImportSessionLookup sessions;
    private final UuidV7 uuids;
    private final Clock clock;

    public ImportIntake(ImportSessionLookup sessions, UuidV7 uuids, Clock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.uuids = Objects.requireNonNull(uuids, "uuids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * @throws IdempotencyConflictException ключ уже занят другим файлом
     */
    public Intake receive(ImportRequest request) {
        Optional<ImportSession> existing =
                sessions.findByIdempotencyKey(request.workspaceId(), request.idempotencyKey());
        if (existing.isPresent()) {
            if (!existing.get().sourceHash().equals(request.sourceHash())) {
                throw new IdempotencyConflictException(existing.get(), request.sourceHash());
            }
            return new Intake(existing.get(), true);
        }
        return new Intake(ImportSession.receive(ImportSessionId.next(uuids), request, clock.instant()), false);
    }

    /**
     * @param replayed {@code true} — сессия уже была, вызывающий отдаёт её результат и
     *                 ничего не применяет повторно
     */
    public record Intake(ImportSession session, boolean replayed) {

        public Intake {
            Objects.requireNonNull(session, "session");
        }
    }
}
