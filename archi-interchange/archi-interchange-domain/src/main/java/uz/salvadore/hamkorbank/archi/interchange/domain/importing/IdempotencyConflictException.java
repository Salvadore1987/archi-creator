package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeDomainException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/**
 * Тот же ключ идемпотентности, другой файл. Это конфликт, а не новый
 * импорт: молча начать вторую сессию под тем же ключом значило бы потерять смысл ключа.
 */
public final class IdempotencyConflictException extends InterchangeDomainException {

    private final transient ImportSession existing;

    IdempotencyConflictException(ImportSession existing) {
        super(InterchangeCodes.IMPORT_IDEMPOTENCY, Message.of(InterchangeMessages.IMPORT_KEY_REUSED));
        this.existing = existing;
    }

    /** Сессия, которая уже заняла ключ. */
    public ImportSession existing() {
        return existing;
    }
}
