package uz.salvadore.hamkorbank.archi.modeling.domain.idempotency;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Результат команды, выполненной с ключом идемпотентности. Хранит отпечаток
 * тела и ссылку на результат, а не сам ответ: повтор перечитывает результат.
 *
 * @param actor subject: ключ одного пользователя не конфликтует с ключом другого
 */
public record IdempotencyRecord(String scope, String actor, String key, String fingerprint, String resultRef,
                                Instant createdAt) {

    public static final int KEY_MAX = 64;
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    public IdempotencyRecord {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(resultRef, "resultRef");
        Objects.requireNonNull(createdAt, "createdAt");
        IdempotentCommand.requireValidKey(key);
        if (fingerprint == null || !SHA256.matcher(fingerprint).matches()) {
            throw new IllegalArgumentException("отпечаток — SHA-256 в hex");
        }
    }
}
