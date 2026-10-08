package uz.salvadore.hamkorbank.archi.bootstrap.roundtrip;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uz.salvadore.hamkorbank.archi.bootstrap.roundtrip.XmlEquivalence.assertXmlEquivalent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiDocumentWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiXmlWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;

/**
 * Golden-file round-trip — критический контур (docs/backend.md §9.1, NFR-05).
 *
 * <p>Цикл «прочитать → записать» обязан дать XML, семантически идентичный исходному
 * (INV-IXC-005), а второй цикл — тот же документ и те же байты (INV-IXC-004).
 * Падение блокирует сборку: в CI этот класс — отдельный шаг.
 *
 * <p>На этапе 1 цикл идёт через кодек; на этапе 2 тот же тест пойдёт через БД.
 */
class RoundTripGoldenFileTest {

    /**
     * Эталон читается из docs/, а не копируется в фикстуры: INV-IXC-005 называет
     * эталоном именно этот файл, и копия молча разошлась бы с ним.
     */
    private static final String REFERENCE = "docs/Hamkorbank_AS_IS_strict.archimate";

    private final ArchiDocumentReader reader = new StaxArchiDocumentReader();
    private final ArchiDocumentWriter writer = new ArchiXmlWriter();

    @Test
    @DisplayName("INV-IXC-005, NFR-05: эталонная модель Hamkorbank проходит round-trip без потерь")
    void hamkorbankAsIsSurvivesRoundTrip() throws IOException {
        byte[] original = Files.readAllBytes(repositoryFile(REFERENCE));

        ModelDocument document = read(original);
        byte[] written = writer.write(document);

        assertXmlEquivalent(original, written);
        assertSecondCycleIsStable(document, written);
        // Охрана от пустого успеха: прочитано столько, сколько в файле есть.
        assertEquals(9, document.folders().size(), "корневые папки");
        assertEquals(402, document.elements().size(), "элементы");
        assertEquals(368, document.relationships().size(), "связи");
        assertEquals(12, document.views().size(), "представления");
    }

    private void assertSecondCycleIsStable(ModelDocument document, byte[] written) {
        ModelDocument reread = read(written);
        assertEquals(document, reread, "второе чтение дало другой документ");
        assertArrayEquals(written, writer.write(reread), "вторая запись дала другие байты");
    }

    private ModelDocument read(byte[] xml) {
        return reader.read(new ByteArrayInputStream(xml));
    }

    /** Файл репозитория — из каталога модуля (так запускает Maven) или из корня (так — IDE). */
    static Path repositoryFile(String relative) {
        return Stream.of(Path.of("..").resolve(relative), Path.of(relative))
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("не найден " + relative + " ни в .., ни в "
                        + Path.of("").toAbsolutePath() + "; " + List.of()));
    }
}
