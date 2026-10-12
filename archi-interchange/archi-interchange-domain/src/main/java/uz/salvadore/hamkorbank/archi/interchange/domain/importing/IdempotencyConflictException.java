package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;

/**
 * Тот же ключ идемпотентности, другой файл. Это конфликт, а не новый
 * импорт: молча начать вторую сессию под тем же ключом значило бы потерять смысл ключа.
 */
public final class IdempotencyConflictException extends RuntimeException {

    public static final String CODE = InterchangeCodes.IDEMPOTENCY_CONFLICT;
    public static final String INVARIANT = InterchangeCodes.IMPORT_IDEMPOTENCY;

    private final transient ImportSession existing;
    private final transient ContentHash submitted;

    IdempotencyConflictException(ImportSession existing, ContentHash submitted) {
        super(INVARIANT + ": " + reason(existing, submitted));
        this.existing = existing;
        this.submitted = submitted;
    }

    public Message reason() {
        return reason(existing, submitted);
    }

    private static Message reason(ImportSession existing, ContentHash submitted) {
        return Message.of(InterchangeMessages.IDEMPOTENCY_KEY_REUSED, existing.idempotencyKey(), existing.sourceHash(),
                submitted);
    }

    public ImportSession existing() {
        return existing;
    }
}
