package uz.salvadore.hamkorbank.archi.interchange.domain.git;

import java.util.Objects;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/**
 * Ссылка на состояние репозитория, как её пишет человек: ветка, тег, полное имя
 * {@code refs/…} или идентификатор коммита, в том числе сокращённый. Что она значит,
 * решает репозиторий; здесь — только то, что это допустимое имя Git.
 */
public record GitRef(String value) {

    /** Наибольшая длина ссылки. */
    public static final int MAX = 255;
    private static final Pattern SHA = Pattern.compile("^[0-9a-f]{7,40}$");

    public GitRef {
        Objects.requireNonNull(value, "value");
        if (value.length() > MAX || !GitNames.valid(value)) {
            throw new InvalidValueException(InterchangeMessages.GIT_REF_INVALID, value);
        }
    }

    /** Ссылка из строки без пробелов по краям; {@code null} — отказ значения. */
    public static GitRef of(String value) {
        if (value == null) {
            throw new InvalidValueException(InterchangeMessages.GIT_REF_INVALID,
                                            Message.of(InterchangeMessages.VALUE_MISSING));
        }
        return new GitRef(value.strip());
    }

    /** Похожа на идентификатор коммита — полный или сокращённый от 7 знаков. */
    public boolean looksLikeSha() {
        return SHA.matcher(value).matches();
    }

    @Override
    public String toString() {
        return value;
    }
}
