package uz.salvadore.hamkorbank.archi.interchange.domain.git;

import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/**
 * Соответствие субъекта Keycloak автору коммита: сервер коммитит от имени архитектора,
 * сохранившего версию, а не от своего.
 */
public record GitAuthor(String subject, String name, String email) {

    /** Наибольшая длина субъекта, имени и почты автора. */
    public static final int FIELD_MAX = 200;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s<>@]+@[^\\s<>@]+$");
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");

    public GitAuthor {
        require(subject);
        require(name);
        require(email);
        if (!EMAIL.matcher(email).matches() || name.contains("<") || name.contains(">")) {
            throw new InvalidValueException(InterchangeMessages.GIT_AUTHOR_INVALID, subject);
        }
    }

    private static void require(String value) {
        if (value == null || value.isBlank() || value.length() > FIELD_MAX || CONTROL.matcher(value).find()) {
            throw new InvalidValueException(InterchangeMessages.GIT_AUTHOR_INVALID,
                                            value == null ? Message.of(InterchangeMessages.VALUE_MISSING) : value);
        }
    }
}
