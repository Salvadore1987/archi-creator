package uz.salvadore.hamkorbank.archi.advisor.domain.identity;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.Objects;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.UuidV7;

/** Ключ бюджета токенов — UUIDv7 приложения. */
public record TokenBudgetId(UUID value) {

    public TokenBudgetId {
        Objects.requireNonNull(value, "TokenBudgetId");
    }

    /** Ключ из значения UUID. */
    public static TokenBudgetId of(UUID value) {
        return new TokenBudgetId(value);
    }

    /** Ключ из строки UUID; неверный формат — {@link IllegalArgumentException}. */
    public static TokenBudgetId of(String value) {
        return new TokenBudgetId(UUIDUtil.uuid(value));
    }

    /** Новый ключ от генератора приложения. */
    public static TokenBudgetId next(UuidV7 uuids) {
        return new TokenBudgetId(uuids.next());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
