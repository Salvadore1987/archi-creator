package uz.salvadore.hamkorbank.archi.interchange.application.mapping;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiDocumentWriter;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotReader;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotWriter;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;

/**
 * Снимок версии modeling — файл {@code .archimate}: сборка из агрегатов и детерминированная
 * запись (INV-IXC-004), а для отката — чтение и раскладка обратно. Формат файла — язык
 * interchange, поэтому порты modeling реализованы здесь (ADR-0017).
 */
public final class ArchimateSnapshots implements SnapshotWriter, SnapshotReader {

    private final ArchiDocumentReader reader;
    private final ArchiDocumentWriter writer;
    private final DocumentAssembler assembler = new DocumentAssembler();
    private final Supplier<UUID> uuids;
    private final Supplier<ArchiId> archiIds;

    public ArchimateSnapshots(ArchiDocumentReader reader, ArchiDocumentWriter writer, Supplier<UUID> uuids,
                              Supplier<ArchiId> archiIds) {
        this.reader = reader;
        this.writer = writer;
        this.uuids = uuids;
        this.archiIds = archiIds;
    }

    @Override
    public byte[] write(ModelContent content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        writer.write(assembler.assemble(content), out);
        return out.toByteArray();
    }

    @Override
    public ModelContent read(byte[] xml, ModelHeader current, Instant now) {
        return new DocumentDecomposer(uuids, archiIds).decompose(reader.read(new ByteArrayInputStream(xml)),
                new DocumentDecomposer.Identity(current.id(), current.workspaceId(), current.createdBy(),
                        current.createdAt(), now, current.version()));
    }
}
