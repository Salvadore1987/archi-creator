package uz.salvadore.hamkorbank.archi.interchange.application.mapping;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiXmlWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeDomainException;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotUnreadableException;

class ArchimateSnapshotsTest {

    /** Идентификаторы тестовых объектов — версии 7, из той же библиотеки, что у приложения. */
    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

    private final ArchimateSnapshots snapshots =
            new ArchimateSnapshots(new StaxArchiDocumentReader(), new ArchiXmlWriter(), IDS::generate);

    @Test
    @DisplayName("Снимок не читается или не раскладывается — отказ порта modeling с причиной от раскладки")
    void unreadableSnapshotIsPortFailure() {
        byte[] malformed = "<archimate:model".getBytes(StandardCharsets.UTF_8);
        byte[] untypedRoot = """
                <?xml version="1.0" encoding="UTF-8"?>
                <archimate:model xmlns:archimate="http://www.archimatetool.com/archimate" name="m" id="id-m" \\
                version="5.0.0">
                  <folder name="Без типа" id="id-f" type="неизвестный"/>
                </archimate:model>
                """.getBytes(StandardCharsets.UTF_8);

        for (byte[] xml : new byte[][] {malformed, untypedRoot}) {
            SnapshotUnreadableException failure = assertThrows(SnapshotUnreadableException.class,
                    () -> snapshots.read(xml));
            assertInstanceOf(InterchangeDomainException.class, failure.getCause());
        }
    }
}
