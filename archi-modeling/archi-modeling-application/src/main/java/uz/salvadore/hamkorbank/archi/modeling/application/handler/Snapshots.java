package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/** Сжатие снимка (gzip, хранится в {@code bytea}) и его отпечаток. */
public final class Snapshots {

    private static final String DIGEST = "SHA-256";

    private Snapshots() {
    }

    /** Снимок, сжатый gzip. */
    public static byte[] gzip(byte[] xml) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(xml.length / 8);
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(xml);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** Снимок из gzip; повреждённый — отказ «дефект сервера» с кодом. */
    public static byte[] gunzip(byte[] compressed) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return gzip.readAllBytes();
        } catch (IOException e) {
            ModelingDomainException corrupted = new ModelingDomainException(ModelingCodes.SNAPSHOT_CORRUPTED,
                    Failure.DEFECT, Message.of(ModelingMessages.SNAPSHOT_CORRUPTED));
            corrupted.initCause(e);
            throw corrupted;
        }
    }

    /** Отпечаток содержимого: SHA-256 в шестнадцатеричной записи. */
    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST).digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(Message.of(ModelingMessages.DIGEST_UNAVAILABLE, DIGEST).toString(), e);
        }
    }
}
