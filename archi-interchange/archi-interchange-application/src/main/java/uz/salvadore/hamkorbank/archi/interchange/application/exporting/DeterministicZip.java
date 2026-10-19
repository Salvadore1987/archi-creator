package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Архив выгрузки, одинаковый байт в байт для одного содержимого: время записей фиксировано,
 * порядок — порядок добавления, имена — в UTF-8. Выгрузку одной версии можно сравнивать
 * по отпечатку и класть рядом с прошлой без ложного диффа.
 */
final class DeterministicZip {

    private static final long FIXED_ENTRY_TIME = 0L;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);

    /** Запись архива; её содержимое — части подряд, например метка порядка байтов и текст. */
    DeterministicZip add(String name, byte[]... parts) {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(FIXED_ENTRY_TIME);
        try {
            zip.putNextEntry(entry);
            for (byte[] part : parts) {
                zip.write(part);
            }
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /** Архив целиком; после этого записи не добавляются. */
    byte[] bytes() {
        try {
            zip.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
