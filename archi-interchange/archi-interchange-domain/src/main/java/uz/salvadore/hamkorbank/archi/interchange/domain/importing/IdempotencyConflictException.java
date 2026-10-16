package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeDomainException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;

/**
 * Тот же ключ идемпотентности, другой файл. Это конфликт, а не новый
 * импорт: молча начать вторую сессию под тем же ключом значило бы потерять смысл ключа.
 */
public final class IdempotencyConflictException extends InterchangeDomainException {

    /** Код ошибки: ключ идемпотентности уже занят файлом с другим содержимым. */
    public static final String CODE = InterchangeCodes.IDEMPOTENCY_CONFLICT;
    /** Код нарушенного правила идемпотентности импорта. */
    public static final String INVARIANT = InterchangeCodes.IMPORT_IDEMPOTENCY;

    private final transient ImportSession existing;
    private final transient ContentHash submitted;

    IdempotencyConflictException(ImportSession existing, ContentHash submitted) {
        super(CODE, reason(existing, submitted));
        this.existing = existing;
        this.submitted = submitted;
    }

    private static Message reason(ImportSession existing, ContentHash submitted) {
        return Message.of(InterchangeMessages.IDEMPOTENCY_KEY_REUSED, existing.idempotencyKey(), existing.sourceHash(),
                submitted);
    }

    /** Сессия, которая уже заняла ключ. */
    public ImportSession existing() {
        return existing;
    }
}
