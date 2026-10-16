package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import java.util.Objects;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;

/**
 * Ссылка на объект модели — единственный способ, которым помощник на неё указывает.
 * Идентификатор Archi — чтобы интерфейс открыл объект, имя — чтобы текст ответа читался.
 */
public record ElementRef(String archiId, String name) {

    private static final Pattern ARCHI_ID = Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9_.-]{0,254}$");

    public ElementRef {
        if (!validArchiId(archiId)) {
            throw new InvalidValueException(AdvisorMessages.ARCHI_ID_INVALID, archiId);
        }
        Objects.requireNonNull(name, "name");
    }

    /** Годится ли строка идентификатором объекта модели: вид и длина идентификатора Archi. */
    public static boolean validArchiId(String archiId) {
        return archiId != null && ARCHI_ID.matcher(archiId).matches();
    }

    /** Ссылка на объект; недопустимый идентификатор — {@link InvalidValueException}. */
    public static ElementRef of(String archiId, String name) {
        return new ElementRef(archiId, name);
    }
}
