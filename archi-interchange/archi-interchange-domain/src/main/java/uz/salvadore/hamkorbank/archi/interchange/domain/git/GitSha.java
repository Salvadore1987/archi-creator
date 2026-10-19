package uz.salvadore.hamkorbank.archi.interchange.domain.git;

import java.util.Objects;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;

/** Идентификатор коммита: 40 hex в нижнем регистре. */
public record GitSha(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[0-9a-f]{40}$");

    public GitSha {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new InvalidValueException(InterchangeMessages.GIT_SHA_INVALID, value);
        }
    }

    /** Идентификатор коммита из строки. */
    public static GitSha of(String value) {
        return new GitSha(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
