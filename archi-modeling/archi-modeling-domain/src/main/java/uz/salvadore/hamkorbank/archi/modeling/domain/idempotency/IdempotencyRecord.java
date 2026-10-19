package uz.salvadore.hamkorbank.archi.modeling.domain.idempotency;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.AggregateRoot;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/**
 * Результат команды, выполненной с ключом идемпотентности. Хранит отпечаток
 * тела и ссылку на результат, а не сам ответ: повтор перечитывает результат.
 */
public final class IdempotencyRecord extends AggregateRoot<IdempotencyRecord.Key> {

    /** Предел длины ключа идемпотентности. */
    public static final int KEY_MAX = 64;
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    /**
     * Тождество записи — операция, субъект и ключ клиента вместе: один ключ у разных
     * пользователей или операций — разные записи.
     *
     * @param scope операция, к которой относится ключ
     * @param actor субъект, приславший ключ
     * @param key   ключ идемпотентности от клиента
     */
    public record Key(String scope, String actor, String key) {
    }

    private final String scope;
    private final String actor;
    private final String key;
    private final String fingerprint;
    private final String resultRef;
    private final Instant createdAt;

    private IdempotencyRecord(String scope, String actor, String key, String fingerprint, String resultRef,
                              Instant createdAt) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(resultRef, "resultRef");
        Objects.requireNonNull(createdAt, "createdAt");
        IdempotentCommand.requireValidKey(key);
        if (fingerprint == null || !SHA256.matcher(fingerprint).matches()) {
            throw new InvalidValueException(ModelingMessages.FINGERPRINT_NOT_SHA256);
        }
        super(new Key(scope, actor, key));
        this.scope = scope;
        this.actor = actor;
        this.key = key;
        this.fingerprint = fingerprint;
        this.resultRef = resultRef;
        this.createdAt = createdAt;
    }

    /** Ключ, только что занятый выполненной командой: её отпечаток и ссылка на результат. */
    public static IdempotencyRecord record(String scope, String actor, String key, String fingerprint,
                                           String resultRef, Instant now) {
        return new IdempotencyRecord(scope, actor, key, fingerprint, resultRef, now);
    }

    /** Сборка из сохранённых значений — для хранилища; значения проверяются, как при создании. */
    public static IdempotencyRecord restore(String scope, String actor, String key, String fingerprint,
                                            String resultRef, Instant createdAt) {
        return new IdempotencyRecord(scope, actor, key, fingerprint, resultRef, createdAt);
    }

    /** Операция, к которой относится ключ. */
    public String scope() {
        return scope;
    }

    /** Subject: ключ одного пользователя не конфликтует с ключом другого. */
    public String actor() {
        return actor;
    }

    /** Ключ идемпотентности от клиента. */
    public String key() {
        return key;
    }

    /** Отпечаток тела команды — SHA-256 в шестнадцатеричной записи. */
    public String fingerprint() {
        return fingerprint;
    }

    /** Ссылка на результат команды: повтор перечитывает его, а не хранит ответ. */
    public String resultRef() {
        return resultRef;
    }

    /** Момент первого выполнения команды. */
    public Instant createdAt() {
        return createdAt;
    }
}
