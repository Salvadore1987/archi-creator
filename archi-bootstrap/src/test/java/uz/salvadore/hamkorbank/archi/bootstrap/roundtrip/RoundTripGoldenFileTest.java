package uz.salvadore.hamkorbank.archi.bootstrap.roundtrip;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uz.salvadore.hamkorbank.archi.bootstrap.roundtrip.XmlEquivalence.assertXmlEquivalent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiDocumentWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiXmlWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentValue;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.RawXmlFragment;

/**
 * Golden-file round-trip — критический контур (docs/backend.md §9.1, NFR-05).
 *
 * <p>Цикл «прочитать → записать» обязан дать XML, семантически идентичный исходному
 * (INV-IXC-005), а второй цикл — тот же документ и те же байты (INV-IXC-004).
 * Падение блокирует сборку: в CI этот класс — отдельный шаг.
 *
 * <p>На этапе 1 цикл идёт через кодек; на этапе 2 тот же тест пойдёт через БД.
 */
public class RoundTripGoldenFileTest {

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

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"capability_and_location", "nested_containment", "all_relationship_types",
            "styled_objects", "unknown_extension"})
    @DisplayName("§9.1: фикстура проходит round-trip без потерь")
    void fixtureSurvivesRoundTrip(String fixture) throws IOException {
        byte[] original = fixture(fixture);

        ModelDocument document = read(original);
        byte[] written = writer.write(document);

        assertXmlEquivalent(original, written);
        assertSecondCycleIsStable(document, written);
    }

    @Test
    @DisplayName("FR-03, INV-IXC-001: непрозрачные узлы и незнакомые атрибуты переживают round-trip дословно")
    void opaqueNodesSurvive() throws IOException {
        byte[] original = fixture("unknown_extension");
        ModelDocument document = read(original);
        String written = new String(writer.write(document), StandardCharsets.UTF_8);

        List<RawXmlFragment> fragments = document.allNodes().stream()
                .flatMap(node -> node.rawFragments().stream())
                .collect(Collectors.toList());
        fragments.addAll(document.orphanFragments());
        // vendor:lineage, два feature, profile, vendor:metadata
        assertEquals(5, fragments.size(), "непрозрачные фрагменты");
        for (RawXmlFragment fragment : fragments) {
            assertTrue(written.contains(fragment.xml()), "фрагмент не выведен дословно: " + fragment.xml());
        }
        assertTrue(written.contains("<!-- комментарий внутри непрозрачного фрагмента сохраняется -->"));
        assertTrue(written.contains("vendor:owner=\"Розница\""), "незнакомый атрибут узла");
        assertTrue(written.contains("xsi:type=\"archimate:QuantumComponent\" name=\"Тип из будущей версии Archi\""),
                "тип из будущей версии Archi");
        assertTrue(written.contains("qubits=\"128\""));
        assertEquals(3, document.views().size(), "диаграмма, скетч и холст — все представления");
    }

    @Test
    @DisplayName("§9.1: вложенность Group → Group → DiagramObject, координаты остаются относительными родителю")
    void nestedBoundsStayRelative() throws IOException {
        ModelDocument document = read(fixture("nested_containment"));

        DocumentNode outer = document.views().getFirst().children().getFirst();
        DocumentNode inner = outer.children().getFirst();
        DocumentNode leaf = inner.children().getFirst();
        for (DocumentNode node : List.of(inner, leaf)) {
            DocumentValue bounds = node.values("bounds").getFirst();
            assertEquals(Optional.of("10"), bounds.attribute("x"), node.archiId().value());
            assertEquals(Optional.of("30"), bounds.attribute("y"), node.archiId().value());
        }
        DocumentNode userFolder = document.folders().getLast().children().getFirst();
        assertEquals(Optional.of("Контекст"), userFolder.attribute("name"));
        assertEquals(document.views().getFirst(), userFolder.children().getFirst(),
                "представление лежит во вложенной пользовательской папке");
    }

    @Test
    @DisplayName("FR-09: фикстура связей содержит все одиннадцать типов и оба вида Junction")
    void allRelationshipTypesArePresent() throws IOException {
        ModelDocument document = read(fixture("all_relationship_types"));

        Set<String> types = document.relationships().stream()
                .map(r -> r.archiType().orElseThrow())
                .collect(Collectors.toSet());
        assertEquals(Set.of("Composition", "Aggregation", "Assignment", "Realization", "Serving", "Access",
                        "Influence", "Triggering", "Flow", "Specialization", "Association").stream()
                .map(name -> "archimate:" + name + "Relationship").collect(Collectors.toSet()), types);
        assertEquals(2, document.elements().stream()
                .filter(e -> e.archiType().equals(Optional.of("archimate:Junction"))).count());
    }

    @Test
    @DisplayName("FR-21: собственный стиль объекта и соединения выходит дословно")
    void ownStyleSurvives() throws IOException {
        String written = new String(writer.write(read(fixture("styled_objects"))), StandardCharsets.UTF_8);

        assertTrue(written.contains("fillColor=\"#b5ffff\" gradient=\"1\" lineColor=\"#003366\""));
        assertTrue(written.contains("lineColor=\"#ff0000\" lineWidth=\"3\""));
        assertTrue(written.contains("font=\"1|Consolas|9.0|0|WINDOWS|1|-12|0|0|0|400|0|0|0|0|3|2|1|49|Consolas\""));
    }

    private void assertSecondCycleIsStable(ModelDocument document, byte[] written) {
        ModelDocument reread = read(written);
        assertEquals(document, reread, "второе чтение дало другой документ");
        assertArrayEquals(written, writer.write(reread), "вторая запись дала другие байты");
    }

    private ModelDocument read(byte[] xml) {
        return reader.read(new ByteArrayInputStream(xml));
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = RoundTripGoldenFileTest.class.getResourceAsStream("/fixtures/" + name + ".archimate")) {
            if (in == null) {
                throw new IllegalStateException("нет фикстуры " + name);
            }
            return in.readAllBytes();
        }
    }

    /** Файл репозитория — из каталога модуля (так запускает Maven) или из корня (так — IDE). */
    public static Path repositoryFile(String relative) {
        return Stream.of(Path.of("..").resolve(relative), Path.of(relative))
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("не найден " + relative + " ни в .., ни в "
                        + Path.of("").toAbsolutePath() + "; " + List.of()));
    }
}
