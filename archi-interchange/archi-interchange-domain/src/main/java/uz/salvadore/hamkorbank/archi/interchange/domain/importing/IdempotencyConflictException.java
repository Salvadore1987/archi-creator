package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;

/**
 * Тот же ключ идемпотентности, другой файл (INV-IXC-003). Это конфликт, а не новый
 * импорт: молча начать вторую сессию под тем же ключом значило бы потерять смысл ключа.
 */
public final class IdempotencyConflictException extends RuntimeException {

    public static final String CODE = "IXC_IDEMPOTENCY_CONFLICT";
    public static final String INVARIANT = "INV-IXC-003";

    private final transient ImportSession existing;

    IdempotencyConflictException(ImportSession existing, ContentHash submitted) {
        super(INVARIANT + ": ключ " + existing.idempotencyKey() + " уже использован для файла "
                + existing.sourceHash() + ", подан " + submitted);
        this.existing = existing;
    }

    public ImportSession existing() {
        return existing;
    }
}
