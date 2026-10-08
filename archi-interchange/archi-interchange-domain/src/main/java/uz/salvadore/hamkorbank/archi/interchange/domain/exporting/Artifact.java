package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import java.util.Arrays;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;

/** Готовый файл выгрузки с отпечатком содержимого. */
public record Artifact(byte[] bytes, String mediaType, String fileName, ContentHash hash) {

    public Artifact {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(hash, "hash");
        bytes = bytes.clone();
        if (!hash.equals(ContentHash.of(bytes))) {
            throw new IllegalArgumentException("отпечаток не соответствует содержимому");
        }
    }

    /** Отпечаток считается по содержимому, а не принимается на веру. */
    public static Artifact of(byte[] bytes, String mediaType, String fileName) {
        return new Artifact(bytes, mediaType, fileName, ContentHash.of(bytes));
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Artifact that && Arrays.equals(bytes, that.bytes) && mediaType.equals(that.mediaType)
                && fileName.equals(that.fileName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash, mediaType, fileName);
    }

    @Override
    public String toString() {
        return "Artifact[" + fileName + ", " + mediaType + ", " + bytes.length + " байт, " + hash + "]";
    }
}
