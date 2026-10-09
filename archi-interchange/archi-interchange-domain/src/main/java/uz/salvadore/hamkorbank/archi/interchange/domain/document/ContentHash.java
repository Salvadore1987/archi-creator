package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/** SHA-256 входного файла или выгруженного артефакта, 64 hex в нижнем регистре. */
public record ContentHash(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[0-9a-f]{64}$");

    private static final String DIGEST = "SHA-256";

    public ContentHash {
        Objects.requireNonNull(value, "contentHash");
        if (!FORMAT.matcher(value).matches()) {
            throw new InvalidValueException(InterchangeMessages.HASH_NOT_SHA256, value);
        }
    }

    public static ContentHash of(byte[] content) {
        try {
            return new ContentHash(HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST).digest(content)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(Message.of(InterchangeMessages.DIGEST_UNAVAILABLE, DIGEST).toString(), e);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
