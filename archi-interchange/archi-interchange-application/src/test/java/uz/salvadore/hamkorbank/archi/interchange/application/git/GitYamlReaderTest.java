package uz.salvadore.hamkorbank.archi.interchange.application.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;

/** Строгость чтения: дерево, которое писатель не мог дать, отклоняется с именем файла. */
class GitYamlReaderTest {

    private static final String MODEL = "format_version: 1\nid: id-1\nname: x\nitems: []\n";
    private final GitYamlReader reader = new GitYamlReader();

    @Test
    @DisplayName("INV-IXC-011: незнакомое поле — отказ, а не молчаливая потеря")
    void unknownFieldIsRejected() {
        InvalidValueException e = assertThrows(InvalidValueException.class, () -> reader.read(Map.of(
                "model.yaml", bytes(MODEL), "elements/other/a.yaml",
                bytes("id: id-a\ntype: Location\nposition: 1000\ncolour: red\n"))));
        assertEquals(InterchangeMessages.GIT_FIELD_UNKNOWN, e.reason().key());
    }

    @Test
    @DisplayName("INV-IXC-011: две записи на одной позиции — отказ")
    void repeatedPositionIsRejected() {
        InvalidValueException e = assertThrows(InvalidValueException.class, () -> reader.read(Map.of(
                "model.yaml", bytes(MODEL),
                "elements/other/a.yaml", bytes("id: id-a\ntype: Location\nposition: 1000\n"),
                "elements/other/b.yaml", bytes("id: id-b\ntype: Location\nposition: 1000\n"))));
        assertEquals(InterchangeMessages.GIT_POSITION_REPEATED, e.reason().key());
    }

    @Test
    @DisplayName("INV-IXC-011: незнакомая версия формата — отказ")
    void unknownFormatVersionIsRejected() {
        InvalidValueException e = assertThrows(InvalidValueException.class,
                () -> reader.read(Map.of("model.yaml",
                        bytes(MODEL.replace("format_version: 1", "format_version: 9")))));
        assertEquals(InterchangeMessages.GIT_FORMAT_VERSION, e.reason().key());
    }

    @Test
    @DisplayName("INV-IXC-011: ссылка на папку, которой нет, — отказ")
    void unknownFolderIsRejected() {
        InvalidValueException e = assertThrows(InvalidValueException.class, () -> reader.read(Map.of(
                "model.yaml", bytes(MODEL),
                "elements/other/a.yaml", bytes("id: id-a\ntype: Location\nfolder: id-f\nposition: 1000\n"))));
        assertEquals(InterchangeMessages.GIT_FOLDER_UNKNOWN, e.reason().key());
    }

    @Test
    @DisplayName("INV-IXC-011: папка с идентификатором модели — отказ, а не бесконечная рекурсия")
    void folderWithModelIdIsRejected() {
        InvalidValueException e = assertThrows(InvalidValueException.class, () -> reader.read(Map.of(
                "model.yaml", bytes(MODEL),
                "folders.yaml", bytes("folders:\n  - id: id-1\n    name: f\n    position: 1000\n"))));
        assertEquals(InterchangeMessages.GIT_ID_REPEATED, e.reason().key());
    }

    @Test
    @DisplayName("INV-IXC-011: папка — родитель самой себе — отказ, а не молча пропавшая папка")
    void folderParentOfItselfIsRejected() {
        InvalidValueException e = assertThrows(InvalidValueException.class, () -> reader.read(Map.of(
                "model.yaml", bytes(MODEL),
                "folders.yaml", bytes("folders:\n  - id: id-f\n    name: f\n    parent: id-f\n    position: 1000\n"))));
        assertEquals(InterchangeMessages.GIT_FOLDER_UNKNOWN, e.reason().key());
    }

    @ParameterizedTest
    @ValueSource(strings = {"!!int 1: a\n", "attributes:\n  !!null '': v\n", "attributes:\n  name: !!int 5\n",
        "items:\n  - !!binary aGk=\n"})
    @DisplayName("INV-IXC-011: явный тег YAML — отказ с полем, а не сбой разбора")
    void explicitTagsAreRejected(String tail) {
        InvalidValueException e = assertThrows(InvalidValueException.class, () -> reader.read(Map.of(
                "model.yaml", bytes("format_version: 1\nid: id-1\nname: x\n" + tail))));
        assertEquals(InterchangeMessages.GIT_FIELD_INVALID, e.reason().key());
    }

    @Test
    @DisplayName("INV-IXC-011: два объекта с одним идентификатором — отказ")
    void repeatedObjectIdIsRejected() {
        InvalidValueException e = assertThrows(InvalidValueException.class, () -> reader.read(Map.of(
                "model.yaml", bytes(MODEL),
                "elements/other/a.yaml", bytes("id: id-a\ntype: Location\nposition: 1000\n"),
                "elements/other/b.yaml", bytes("id: id-a\ntype: Location\nposition: 2000\n"))));
        assertEquals(InterchangeMessages.GIT_ID_REPEATED_IN_MODEL, e.reason().key());
    }

    @Test
    @DisplayName("INV-IXC-011: битый UTF-8 — отказ, а не замена символов")
    void malformedUtf8IsRejected() {
        byte[] model = bytes(MODEL.replace("name: x", "name: xx"));
        model[MODEL.indexOf("name: x") + "name: x".length()] = (byte) 0xC3;
        InvalidValueException e = assertThrows(InvalidValueException.class,
                () -> reader.read(Map.of("model.yaml", model)));
        assertEquals(InterchangeMessages.GIT_FILE_UNREADABLE, e.reason().key());
    }

    @Test
    @DisplayName("INV-IXC-011: текст отказа разбора — место в файле, а не сообщение библиотеки")
    void unreadableYamlNamesPlace() {
        InvalidValueException e = assertThrows(InvalidValueException.class,
                () -> reader.read(Map.of("model.yaml", bytes("format_version: 1\nid: [unclosed\n"))));
        assertEquals(InterchangeMessages.GIT_FILE_UNREADABLE_AT, e.reason().key());
        assertEquals(3, e.reason().args().size());
    }

    @Test
    @DisplayName("INV-IXC-011: корень файла — не отображение — свой отказ")
    void nonMappingRootIsRejected() {
        InvalidValueException e = assertThrows(InvalidValueException.class,
                () -> reader.read(Map.of("model.yaml", bytes("- a\n- b\n"))));
        assertEquals(InterchangeMessages.GIT_FILE_NOT_MAPPING, e.reason().key());
    }

    @Test
    @DisplayName("INV-IXC-011: глубокая вложенность групп, которую пишет писатель, читается")
    void deepNestingIsReadable() {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<archimate:model"
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " xmlns:archimate=\"http://www.archimatetool.com/archimate\" name=\"x\" id=\"id-1\""
                + " version=\"5.0.0\">\n<folder name=\"Views\" id=\"id-2\" type=\"diagrams\">\n"
                + "<element xsi:type=\"archimate:ArchimateDiagramModel\" name=\"v\" id=\"id-v\">\n");
        int depth = 60;
        for (int i = 0; i < depth; i++) {
            xml.append("<child xsi:type=\"archimate:Group\" id=\"id-g").append(i).append("\" name=\"g\">")
                    .append("<bounds x=\"1\" y=\"1\" width=\"10\" height=\"10\"/>\n");
        }
        xml.append("</child>\n".repeat(depth)).append("</element>\n</folder>\n</archimate:model>\n");
        ModelDocument document = new StaxArchiDocumentReader().read(new ByteArrayInputStream(bytes(xml.toString())));

        assertEquals(document, reader.read(new GitYamlWriter().write(document, GitPositions.none())));
    }

    @Test
    @DisplayName("INV-IXC-011: каталог модели больше предела — отказ до разбора")
    void oversizedDirectoryIsRejected() {
        Map<String, byte[]> files = new HashMap<>();
        files.put("model.yaml", bytes(MODEL));
        for (int i = 0; i <= GitYamlReader.MAX_FILES; i++) {
            files.put("elements/other/" + i + ".yaml", new byte[0]);
        }
        InvalidValueException e = assertThrows(InvalidValueException.class, () -> reader.read(files));
        assertEquals(InterchangeMessages.GIT_DIRECTORY_TOO_LARGE, e.reason().key());
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
