package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/** Сжатие снимка (gzip, хранится в {@code bytea}) и его отпечаток. */
final class Snapshots {

    private static final String DIGEST = "SHA-256";

    private Snapshots() {
    }

    static byte[] gzip(byte[] xml) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(xml.length / 8);
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(xml);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    static byte[] gunzip(byte[] compressed) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return gzip.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(Message.of(ModelingMessages.SNAPSHOT_CORRUPTED).toString(), e);
        }
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST).digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(Message.of(ModelingMessages.DIGEST_UNAVAILABLE, DIGEST).toString(), e);
        }
    }
}
