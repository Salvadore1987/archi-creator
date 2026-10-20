package uz.salvadore.hamkorbank.archi.interchange.application.mapping;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiDocumentWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeDomainException;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.ModelRows;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotReader;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotUnreadableException;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotWriter;

/**
 * Снимок версии modeling — файл {@code .archimate}: сборка из строк модели и детерминированная
 * запись, а для отката — чтение и раскладка обратно в строки. Формат файла — язык
 * interchange, поэтому порты modeling реализованы здесь.
 */
public final class ArchimateSnapshots implements SnapshotWriter, SnapshotReader {

    private final ArchiDocumentReader reader;
    private final ArchiDocumentWriter writer;
    private final DocumentAssembler assembler = new DocumentAssembler();
    private final Supplier<UUID> uuids;

    /** Внутренние ключи строк при раскладке — из {@code uuids}. */
    public ArchimateSnapshots(ArchiDocumentReader reader, ArchiDocumentWriter writer, Supplier<UUID> uuids) {
        this.reader = reader;
        this.writer = writer;
        this.uuids = uuids;
    }

    @Override
    public byte[] write(ModelRows rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        writer.write(assembler.assemble(rows), out);
        return out.toByteArray();
    }

    /** Файл не читается или не раскладывается в строки — отказ порта с причиной от раскладки. */
    @Override
    public ModelRows read(byte[] xml) {
        try {
            return new DocumentDecomposer(uuids).decompose(reader.read(new ByteArrayInputStream(xml)));
        } catch (InterchangeDomainException unreadable) {
            throw new SnapshotUnreadableException(unreadable);
        }
    }
}
