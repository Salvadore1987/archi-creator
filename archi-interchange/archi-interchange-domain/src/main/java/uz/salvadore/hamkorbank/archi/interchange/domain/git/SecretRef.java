package uz.salvadore.hamkorbank.archi.interchange.domain.git;

import java.util.Objects;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;

/**
 * Ссылка на секрет, а не сам секрет: {@code env:ИМЯ} — переменная окружения сервера.
 * Токен в домене и в базе не появляется; значение по ссылке получает адаптер в момент
 * обращения к репозиторию.
 */
public record SecretRef(String value) {

    private static final Pattern FORMAT = Pattern.compile("^env:[A-Za-z_][A-Za-z0-9_.]{0,199}$");
    private static final String ENV = "env:";

    public SecretRef {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new InvalidValueException(InterchangeMessages.GIT_SECRET_REF_INVALID, value);
        }
    }

    /** Ссылка на секрет из строки вида {@code env:ИМЯ}. */
    public static SecretRef of(String value) {
        return new SecretRef(value);
    }

    /** Имя переменной окружения. */
    public String variable() {
        return value.substring(ENV.length());
    }
}
