package uz.salvadore.hamkorbank.archi.modeling.domain.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;

/**
 * Решение по команде с ключом идемпотентности (INV-MDL-003): тот же ключ и то же тело —
 * результат первого выполнения без нового объекта и новой версии; тот же ключ с другим
 * телом — конфликт, а не новое выполнение.
 */
public final class IdempotentCommand {

    public static final String INVARIANT = "INV-MDL-003";

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

    public static Decision decide(Optional<IdempotencyRecord> existing, String fingerprint) {
        if (existing.isEmpty()) {
            return new Execute();
        }
        if (!existing.get().fingerprint().equals(fingerprint)) {
            throw new ModelingException(INVARIANT, Failure.CONFLICT,
                    "ключ идемпотентности уже использован для другого запроса",
                    Map.of("idempotencyKey", existing.get().key()));
        }
        return new Replay(existing.get().resultRef());
    }

    /** Отпечаток тела команды: SHA-256 от канонической записи её полей. */
    public static String fingerprint(Object... fields) {
        StringBuilder canonical = new StringBuilder();
        for (Object field : fields) {
            String value = String.valueOf(field instanceof Optional<?> o ? o.orElse(null) : field);
            canonical.append(value.length()).append(':').append(value).append('|');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 обязан быть в любой JDK", e);
        }
    }

    public static void requireValidKey(String key) {
        if (key == null || key.isBlank() || key.length() > IdempotencyRecord.KEY_MAX) {
            throw ModelingException.invalid("ключ идемпотентности пуст или длиннее "
                    + IdempotencyRecord.KEY_MAX + " символов");
        }
    }
}
