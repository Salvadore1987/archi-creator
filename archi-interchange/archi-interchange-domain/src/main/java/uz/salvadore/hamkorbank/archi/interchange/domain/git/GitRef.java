package uz.salvadore.hamkorbank.archi.interchange.domain.git;

import java.util.Objects;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;

/**
 * Ссылка на состояние репозитория, как её пишет человек: ветка, тег, полное имя
 * {@code refs/…} или идентификатор коммита, в том числе сокращённый. Что она значит,
 * решает репозиторий; здесь — только то, что это допустимое имя Git.
 */
public record GitRef(String value) {

    public static final int MAX = 255;
    private static final Pattern NAME = Pattern.compile("^(?![/-])(?!.*//)(?!.*\\.\\.)(?!.*@\\{)(?!.*\\.lock$)"
            + "(?!.*/$)(?!.*\\.$)[^\\s~^:?*\\[\\\\\\x00-\\x1f\\x7f]+$");
    private static final Pattern SHA = Pattern.compile("^[0-9a-f]{7,40}$");

    public GitRef {
        Objects.requireNonNull(value, "value");
        if (value.length() > MAX || !NAME.matcher(value).matches()) {
            throw new InvalidValueException(InterchangeMessages.GIT_REF_INVALID, value);
        }
    }

    public static GitRef of(String value) {
        return new GitRef(String.valueOf(value).strip());
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
