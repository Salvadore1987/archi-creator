package uz.salvadore.hamkorbank.archi.interchange.application.git;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiXmlWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;

/**
 * Разложенный YAML: детерминированность, обратимость и раскладка по файлам.
 *
 * <p>Эталоны деревьев — {@code archi-bootstrap/src/test/resources/fixtures/git_roundtrip/<фикстура>/};
 * пересъёмка: {@code ./mvnw -P '!frontend' -pl archi-interchange/archi-interchange-application -am test
 * -Dtest=GitYamlWriterTest -Dgit.fixtures.update=true -Dsurefire.failIfNoSpecifiedTests=false}.
 */
class GitYamlWriterTest {

    private static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();
    private static final Path FIXTURES = ROOT.resolve("archi-bootstrap/src/test/resources/fixtures");
    private static final Path REFERENCE = ROOT.resolve("docs/Hamkorbank_AS_IS_strict.archimate");

    private final GitYamlWriter writer = new GitYamlWriter();
    private final GitYamlReader reader = new GitYamlReader();

    static Stream<Path> sources() throws IOException {
        try (Stream<Path> files = Files.list(FIXTURES)) {
            return Stream.concat(Stream.of(REFERENCE),
                    files.filter(f -> f.toString().endsWith(".archimate")).sorted().toList().stream());
        }
    }

    @Test
    @DisplayName("FR-40, INV-IXC-011: повторная запись без изменений даёт побайтово то же дерево")
    void writingTwiceProducesIdenticalBytes() throws IOException {
        ModelDocument document = read(Files.readAllBytes(REFERENCE));

        SortedMap<String, byte[]> first = writer.write(document, GitPositions.none());
        SortedMap<String, byte[]> second = writer.write(document, GitPositions.none());
        SortedMap<String, byte[]> overPublished = writer.write(document, reader.positions(first));

        assertTreesEqual(first, second);
        assertTreesEqual(first, overPublished);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    @DisplayName("FR-40, INV-IXC-011: из дерева собирается тот же документ и те же байты .archimate")
    void documentSurvivesYaml(Path source) throws IOException {
        ModelDocument original = read(Files.readAllBytes(source));

        ModelDocument restored = reader.read(writer.write(original, GitPositions.none()));

        assertEquals(original, restored);
        assertArrayEquals(new ArchiXmlWriter().write(original), new ArchiXmlWriter().write(restored));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"unknown_extension", "styled_objects"})
    @DisplayName("FR-40: дерево фикстуры совпадает с эталоном побайтово")
    void exportIsByteStable(String name) throws IOException {
        SortedMap<String, byte[]> files = writer.write(read(Files.readAllBytes(
                FIXTURES.resolve(name + ".archimate"))), GitPositions.none());
        Path expected = FIXTURES.resolve("git_roundtrip").resolve(name);
        if (Boolean.getBoolean("git.fixtures.update")) {
            if (Files.exists(expected)) {
                try (Stream<Path> old = Files.walk(expected)) {
                    old.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                Path target = expected.resolve(file.getKey());
                Files.createDirectories(target.getParent());
                Files.write(target, file.getValue());
            }
        }
        SortedMap<String, byte[]> golden = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(expected)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                golden.put(expected.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        }
        assertTreesEqual(golden, files);
    }

    @Test
    @DisplayName("INV-IXC-011: принадлежность папке — поле folder, переименование папки меняет один файл")
    void folderIsFieldNotDirectory() throws IOException {
        String xml = Files.readString(REFERENCE);
        SortedMap<String, byte[]> before = writer.write(read(xml), GitPositions.none());
        String iabs = path(before, "elements/application/iabs.488a7b5a.yaml");

        assertTrue(iabs.contains("\nfolder: id-"), "папка — поле внутри файла");
        assertTrue(before.keySet().stream().noneMatch(p -> p.contains("Capability")), "папок в путях нет");

        String renamed = xml.replace("name=\"Capability map\"", "name=\"Карта способностей\"");
        SortedMap<String, byte[]> after = writer.write(read(renamed), reader.positions(before));

        assertEquals(Set.of("folders.yaml"), changed(before, after));
    }

    @Test
    @DisplayName("INV-IXC-011: вставка элемента в начало папки не перенумеровывает соседей")
    void insertionKeepsNeighbourPositions() throws IOException {
        String xml = Files.readString(REFERENCE);
        SortedMap<String, byte[]> before = writer.write(read(xml), GitPositions.none());

        String anchor = "<folder name=\"Клиентские сервисы\" id=\"id-91189ae76c36ada7f7f7fbe0\">\n";
        String inserted = xml.replace(anchor, anchor + "        <element xsi:type=\"archimate:Capability\""
                + " name=\"Новая способность\" id=\"id-0123456789abcdef01234567\"/>\n");
        SortedMap<String, byte[]> after = writer.write(read(inserted), reader.positions(before));

        Set<String> changed = changed(before, after);
        assertEquals(1, changed.size(), () -> "изменились: " + changed);
        assertTrue(path(after, changed.iterator().next()).contains("position: 500\n"));
        assertArrayEquals(new ArchiXmlWriter().write(read(inserted)),
                new ArchiXmlWriter().write(reader.read(after)));
    }

    @Test
    @DisplayName("INV-IXC-011: строки с переводами, кавычками и служебными символами YAML переживают запись")
    void awkwardTextSurvivesYaml() throws IOException {
        String[] texts = {"", " ведущий пробел", "хвост ", "ключ: значение", "#решётка", "- дефис", "- ",
                "yes", "null", "~", "24", "---", "строка\nвторая", "хвост\n", "два хвоста\n\n", "\nпервая пустая",
                "  отступ\nи нет", "кавычка \" и \\ слэш", "таб\tвнутри", "LS PS ", "@at", "`бэктик`",
                "{фигурные}", "[квадратные]", "a #b", "пробелы   \n   и ещё", "&якорь", "*ссылка", "!тег", "%процент"};
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<archimate:model"
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " xmlns:archimate=\"http://www.archimatetool.com/archimate\" name=\"x\" id=\"id-1\" version=\"5.0.0\">\n"
                + "  <folder name=\"Business\" id=\"id-2\" type=\"business\">\n");
        for (int i = 0; i < texts.length; i++) {
            String escaped = escape(texts[i]);
            xml.append("    <element xsi:type=\"archimate:BusinessActor\" name=\"").append(escaped)
                    .append("\" id=\"id-a").append(i).append("\">\n      <documentation>").append(escaped)
                    .append("</documentation>\n      <property key=\"").append(escaped).append("\" value=\"")
                    .append(escaped).append("\"/>\n    </element>\n");
        }
        xml.append("  </folder>\n</archimate:model>\n");
        ModelDocument original = read(xml.toString());

        assertEquals(original, reader.read(writer.write(original, GitPositions.none())));
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;").replace("\n", "&#xA;")
                .replace("\t", "&#x9;");
    }

    private static ModelDocument read(String xml) {
        return read(xml.getBytes(StandardCharsets.UTF_8));
    }

    private static ModelDocument read(byte[] xml) {
        return new StaxArchiDocumentReader().read(new ByteArrayInputStream(xml));
    }

    private static String path(Map<String, byte[]> files, String path) {
        return new String(files.get(path), StandardCharsets.UTF_8);
    }

    private static Set<String> changed(Map<String, byte[]> before, Map<String, byte[]> after) {
        Set<String> paths = new TreeSet<>(before.keySet());
        paths.addAll(after.keySet());
        return paths.stream().filter(p -> !before.containsKey(p) || !after.containsKey(p)
                || !java.util.Arrays.equals(before.get(p), after.get(p))).collect(Collectors.toCollection(TreeSet::new));
    }

    private static void assertTreesEqual(Map<String, byte[]> expected, Map<String, byte[]> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (String path : expected.keySet()) {
            assertEquals(new String(expected.get(path), StandardCharsets.UTF_8),
                    new String(actual.get(path), StandardCharsets.UTF_8), path);
        }
    }
}
