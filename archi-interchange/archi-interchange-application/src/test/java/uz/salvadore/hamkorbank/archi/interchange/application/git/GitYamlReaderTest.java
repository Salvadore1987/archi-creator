package uz.salvadore.hamkorbank.archi.interchange.application.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;

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

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
