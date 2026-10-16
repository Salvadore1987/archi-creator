package uz.salvadore.hamkorbank.archi.modeling.domain.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.BaseId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/**
 * Решение по команде с ключом идемпотентности: тот же ключ и то же тело —
 * результат первого выполнения без нового объекта и новой версии; тот же ключ с другим
 * телом — конфликт, а не новое выполнение.
 */
public final class IdempotentCommand {

    /** Код отказа, когда ключ идемпотентности повторён с другим телом команды. */
    public static final String INVARIANT = ModelingCodes.IDEMPOTENCY;
    private static final String ABSENT = "-1";
    private static final String DIGEST = "SHA-256";

    private IdempotentCommand() {
    }

    /** Что делать с командой. */
    public sealed interface Decision permits Execute, Replay {
    }

    /** Ключ свободен — выполнить и записать результат. */
    public record Execute() implements Decision {
    }

    /** Ключ уже исполнен с тем же телом — отдать ссылку на первый результат. */
    public record Replay(String resultRef) implements Decision {
    }

    /**
     * Решение по команде: выполнить заново или отдать первый результат.
     *
     * @param existing запись по ключу; {@code null} — ключ ещё не исполнялся
     * @throws ModelingDomainException ключ уже использован с другим отпечатком тела
     */
    public static Decision decide(IdempotencyRecord existing, String fingerprint) {
        if (existing == null) {
            return new Execute();
        }
        if (!existing.fingerprint().equals(fingerprint)) {
            throw new ModelingDomainException(INVARIANT, Failure.CONFLICT,
                    Message.of(ModelingMessages.IDEMPOTENCY_KEY_REUSED),
                    Map.of("idempotencyKey", existing.key()));
        }
        return new Replay(existing.resultRef());
    }

    /**
     * Отпечаток тела команды: SHA-256 от канонической записи её полей. Поле — длина,
     * двоеточие и текст; отсутствующее значение ({@code null}, пустой {@code Optional}) —
     * длина {@code -1}, которой у текста не бывает, поэтому оно не совпадёт со строкой {@code "null"}.
     *
     * <p>Поля — простые значения: текст, число, логическое, перечисление, UUID, ключ сущности.
     * Составное значение передаётся по полям: его {@code toString} изменится с новым полем,
     * и ключи, записанные раньше, перестанут совпадать.
     *
     * @throws IllegalArgumentException поле — составное значение; это ошибка вызывающего кода
     */
    public static String fingerprint(Object... fields) {
        StringBuilder canonical = new StringBuilder();
        for (Object field : fields) {
            Object present = field instanceof Optional<?> o ? o.orElse(null) : field;
            if (present != null && !plain(present)) {
                throw new IllegalArgumentException(present.getClass().getName());
            }
            if (present == null) {
                canonical.append(ABSENT).append('|');
            } else {
                String value = String.valueOf(present);
                canonical.append(value.length()).append(':').append(value).append('|');
            }
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST)
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(Message.of(ModelingMessages.DIGEST_UNAVAILABLE, DIGEST).toString(), e);
        }
    }

    private static boolean plain(Object value) {
        return value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Enum<?> || value instanceof UUID || value instanceof BaseId<?>;
    }

    /** Проверка ключа идемпотентности: не пустой и не длиннее предела. */
    public static void requireValidKey(String key) {
        if (key == null || key.isBlank() || key.length() > IdempotencyRecord.KEY_MAX) {
            throw ModelingDomainException.invalid(
                    Message.of(ModelingMessages.IDEMPOTENCY_KEY_INVALID, IdempotencyRecord.KEY_MAX));
        }
    }
}
