package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** SHA-256 входного файла или выгруженного артефакта, 64 hex в нижнем регистре. */
public record ContentHash(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[0-9a-f]{64}$");

    public ContentHash {
        Objects.requireNonNull(value, "contentHash");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("ожидался SHA-256 в hex: " + value);
        }
    }

    public static ContentHash of(byte[] content) {
        try {
            return new ContentHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 обязан быть в любой JDK", e);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
