package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import java.util.Arrays;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;

/**
 * Готовый файл выгрузки с отпечатком содержимого. Отпечаток считается по самому содержимому
 * при создании, а не принимается на веру, — поэтому собрать файл с чужим отпечатком нельзя.
 */
public final class Artifact {

    private final byte[] bytes;
    private final String mediaType;
    private final String fileName;
    private final ContentHash hash;

    private Artifact(byte[] bytes, String mediaType, String fileName) {
        this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
        this.mediaType = Objects.requireNonNull(mediaType, "mediaType");
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.hash = ContentHash.of(this.bytes);
    }

    /** Файл из содержимого; массив копируется. */
    public static Artifact of(byte[] bytes, String mediaType, String fileName) {
        return new Artifact(bytes, mediaType, fileName);
    }

    /** Содержимое — копией: файл неизменяем. */
    public byte[] bytes() {
        return bytes.clone();
    }

    /** Тип содержимого: {@code application/xml}, {@code application/zip}, … */
    public String mediaType() {
        return mediaType;
    }

    /** Имя файла для ответа. */
    public String fileName() {
        return fileName;
    }

    /** Отпечаток содержимого. */
    public ContentHash hash() {
        return hash;
    }

    /** Размер содержимого в байтах — без копирования массива. */
    public int size() {
        return bytes.length;
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
        return "Artifact[" + fileName + ", " + mediaType + ", " + bytes.length + " B, " + hash + "]";
    }
}
