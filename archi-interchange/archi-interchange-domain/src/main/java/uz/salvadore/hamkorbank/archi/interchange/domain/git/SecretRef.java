package uz.salvadore.hamkorbank.archi.interchange.domain.git;

import java.util.Objects;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;

/**
 * Ссылка на секрет, а не сам секрет: {@code env:ARCHI_GIT_…} — переменная окружения сервера.
 * Токен в домене и в базе не появляется; значение по ссылке получает адаптер в момент
 * обращения к репозиторию.
 *
 * <p>Имя — только с префиксом {@link #PREFIX}: токен уходит паролем на адрес привязки, и ссылка
 * на любую переменную позволила бы отправить туда чужой секрет сервера — ключ API или пароль БД.
 * Значение, не прошедшее проверку, в отказ не попадает: на его месте часто сам токен.
 */
public record SecretRef(String value) {

    /** Префикс имени переменной, в которой лежит токен Git. */
    public static final String PREFIX = "ARCHI_GIT_";
    private static final String ENV = "env:";
    private static final Pattern FORMAT = Pattern.compile("^" + ENV + PREFIX + "[A-Z0-9_]{1,180}$");

    public SecretRef {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new InvalidValueException(InterchangeMessages.GIT_SECRET_REF_INVALID);
        }
    }

    /** Ссылка на секрет из строки вида {@code env:ARCHI_GIT_ИМЯ}. */
    public static SecretRef of(String value) {
        return new SecretRef(value);
    }

    /** Имя переменной окружения. */
    public String variable() {
        return value.substring(ENV.length());
    }
}
