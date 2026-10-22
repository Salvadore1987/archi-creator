package uz.salvadore.hamkorbank.archi.interchange.domain.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;

/**
 * Повреждённые данные отклоняются — с кодом, объектом и строкой.
 * Независимость от строгости проверяет {@code StrictImportTest}; здесь — что
 * читатель находит каждый вид повреждения и называет место.
 */
class CorruptDocumentTest {

    @Test
    @DisplayName("FR-50: невалидный XML — отказ с номером строки и позиции")
    void malformedXmlIsRejectedWithLocation() {
        DocumentDefect defect = single("""
                <?xml version="1.0" encoding="UTF-8"?>
                <archimate:model xmlns:archimate="http://www.archimatetool.com/archimate" id="id-1">
                  <folder name="Business" id="id-2" type="business">
                </archimate:model>
                """);

        assertEquals(InterchangeCodes.MALFORMED_XML, defect.code());
        assertEquals(4, defect.line());
        assertNotNull(defect.column());
    }

    @Test
    @DisplayName("FR-50: поломка XML не отбрасывает дефекты, найденные до неё")
    void malformedXmlKeepsEarlierDefects() {
        CorruptDocumentException e = reject("""
                <?xml version="1.0" encoding="UTF-8"?>
                <archimate:model xmlns:archimate="http://www.archimatetool.com/archimate" id="id-1">
                  <folder name="Business" id="id-1" type="business"/>
                  <folder name="Other" id="id-2" type="other">
                </archimate:model>
                """);

        assertEquals(List.of(InterchangeCodes.DUPLICATE_ID, InterchangeCodes.MALFORMED_XML), codes(e));
    }

    @Test
    @DisplayName("FR-50: ссылка на несуществующий элемент")
    void danglingReferenceIsRejected() {
        DocumentDefect defect = single(Xml.model("""
                <folder name="Relations" id="id-f7" type="relations">
                  <element xsi:type="archimate:FlowRelationship" id="id-r1" source="id-e1" target="id-e1"/>
                </folder>
                <folder name="Business" id="id-f2" type="business">
                  <element xsi:type="archimate:BusinessActor" name="A" id="id-e2"/>
                </folder>
                """));

        // Две ссылки на один и тот же отсутствующий id — две находки, но обе об id-e1.
        assertEquals(InterchangeCodes.DANGLING_REFERENCE, defect.code());
        assertEquals("id-r1", defect.archiId());
        assertEquals(4, defect.line());
    }

    @Test
    @DisplayName("FR-50: дубль идентификатора, в том числе внутри непрозрачного фрагмента")
    void duplicateIdIsRejected() {
        CorruptDocumentException e = reject(Xml.model("""
                <folder name="Business" id="id-f2" type="business">
                  <element xsi:type="archimate:BusinessActor" name="A" id="id-e1"/>
                  <element xsi:type="archimate:BusinessRole" name="B" id="id-e1"/>
                </folder>
                <profile name="P" id="id-f2"/>
                """));

        assertEquals(List.of(InterchangeCodes.DUPLICATE_ID, InterchangeCodes.DUPLICATE_ID), codes(e));
        assertEquals("id-e1", e.defects().getFirst().archiId());
        assertEquals("id-f2", e.defects().get(1).archiId());
    }

    @Test
    @DisplayName("FR-50: связь без одного из концов")
    void relationshipWithoutEndIsRejected() {
        DocumentDefect defect = single(Xml.model("""
                <folder name="Business" id="id-f2" type="business">
                  <element xsi:type="archimate:BusinessActor" name="A" id="id-e1"/>
                </folder>
                <folder name="Relations" id="id-f7" type="relations">
                  <element xsi:type="archimate:ServingRelationship" id="id-r1" source="id-e1"/>
                </folder>
                """));

        assertEquals(InterchangeCodes.MISSING_END, defect.code());
        assertEquals("id-r1", defect.archiId());
    }

    @Test
    @DisplayName("INV-IXC-007: корневая папка без типа — повреждение, вложенная — нет")
    void rootFolderWithoutTypeIsRejected() {
        DocumentDefect defect = single(Xml.model("""
                <folder name="Business" id="id-f1">
                  <folder name="Вложенная" id="id-f2"/>
                </folder>
                """));

        assertEquals(InterchangeCodes.MISSING_ATTRIBUTE, defect.code());
        assertEquals("id-f1", defect.archiId());
    }

    @Test
    @DisplayName("INV-IXC-007: узел не на своём месте — повреждение с местом каждого")
    void misplacedNodesAreRejected() {
        CorruptDocumentException e = reject(Xml.model("""
                <folder name="Business" id="id-f1" type="business">
                  <child xsi:type="archimate:DiagramObject" id="id-n1"/>
                  <element xsi:type="archimate:BusinessActor" name="A" id="id-e1">
                    <element xsi:type="archimate:BusinessRole" name="B" id="id-e2"/>
                  </element>
                </folder>
                <folder name="Views" id="id-f9" type="diagrams">
                  <element xsi:type="archimate:ArchimateDiagramModel" name="V" id="id-v1">
                    <folder name="Лишняя" id="id-f3"/>
                  </element>
                </folder>
                """));

        assertEquals(List.of(InterchangeCodes.MISPLACED_NODE, InterchangeCodes.MISPLACED_NODE,
                InterchangeCodes.MISPLACED_NODE), codes(e));
        assertEquals(List.of("id-n1", "id-e2", "id-f3"), e.defects().stream().map(DocumentDefect::archiId).toList());
    }

    @Test
    @DisplayName("INV-IXC-001: узел без id или без xsi:type не принимается")
    void nodeWithoutRequiredAttributesIsRejected() {
        CorruptDocumentException e = reject(Xml.model("""
                <folder name="Business" type="business">
                  <element name="без типа" id="id-e1"/>
                </folder>
                <folder name="Other" id="id 6" type="other"/>
                """));

        assertEquals(List.of(InterchangeCodes.MISSING_ATTRIBUTE, InterchangeCodes.INVALID_ID), codes(e));
    }

    @Test
    @DisplayName("targetConnections, не совпадающий с соединениями узла, — повреждение")
    void targetConnectionsMismatchIsRejected() {
        DocumentDefect defect = single(Xml.model("""
                <folder name="Views" id="id-f8" type="diagrams">
                  <element xsi:type="archimate:ArchimateDiagramModel" name="V" id="id-v1">
                    <child xsi:type="archimate:Note" id="id-n1" targetConnections="id-c9">
                      <bounds x="0" y="0" width="10" height="10"/>
                    </child>
                    <child xsi:type="archimate:Note" id="id-n2">
                      <bounds x="0" y="0" width="10" height="10"/>
                      <sourceConnection xsi:type="archimate:Connection" id="id-c1" source="id-n2" target="id-n1"/>
                    </child>
                    <child xsi:type="archimate:Note" id="id-c9"/>
                  </element>
                </folder>
                """));

        assertEquals(InterchangeCodes.TARGET_CONNECTIONS_MISMATCH, defect.code());
        assertEquals("id-n1", defect.archiId());
    }

    @Test
    @DisplayName("ожидаемые соединения в тексте дефекта — в порядке файла, а не хеш-таблицы")
    void expectedConnectionsAreListedInFileOrder() {
        DocumentDefect defect = single(Xml.model("""
                <folder name="Views" id="id-f8" type="diagrams">
                  <element xsi:type="archimate:ArchimateDiagramModel" name="V" id="id-v1">
                    <child xsi:type="archimate:Note" id="id-n1" targetConnections="id-c9">
                      <bounds x="0" y="0" width="10" height="10"/>
                    </child>
                    <child xsi:type="archimate:Note" id="id-n2">
                      <bounds x="0" y="0" width="10" height="10"/>
                      <sourceConnection xsi:type="archimate:Connection" id="id-c1" source="id-n2" target="id-n1"/>
                      <sourceConnection xsi:type="archimate:Connection" id="id-c2" source="id-n2" target="id-n1"/>
                    </child>
                    <child xsi:type="archimate:Note" id="id-c9"/>
                  </element>
                </folder>
                """));

        assertEquals("[id-c1, id-c2]", String.valueOf(defect.message().args().get(1)));
    }

    @Test
    @DisplayName("все дефекты находятся за один проход, а не по одному")
    void allDefectsAreCollected() {
        CorruptDocumentException e = reject(Xml.model("""
                <folder name="Relations" id="id-f7" type="relations">
                  <element xsi:type="archimate:FlowRelationship" id="id-r1" target="id-x"/>
                  <element xsi:type="archimate:FlowRelationship" id="id-r1" source="id-y" target="id-y"/>
                </folder>
                """));

        assertEquals(List.of(InterchangeCodes.MISSING_END, InterchangeCodes.DUPLICATE_ID,
                InterchangeCodes.DANGLING_REFERENCE, InterchangeCodes.DANGLING_REFERENCE,
                InterchangeCodes.DANGLING_REFERENCE), codes(e));
    }

    @Test
    @DisplayName("внешние сущности не раскрываются: файл приходит от пользователя")
    void externalEntitiesAreNotResolved() {
        DocumentDefect defect = single("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE m [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <archimate:model xmlns:archimate="http://www.archimatetool.com/archimate" id="id-1" name="&xxe;"/>
                """);

        assertEquals(InterchangeCodes.MALFORMED_XML, defect.code());
    }

    @Test
    @DisplayName("отказ «файл повреждён» без единого дефекта не строится")
    void corruptionWithoutDefectsIsRejected() {
        List<DocumentDefect> none = List.of();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new CorruptDocumentException(none));

        assertEquals(InterchangeMessages.CORRUPT_WITHOUT_DEFECTS, e.getMessage());
    }

    private static DocumentDefect single(String xml) {
        CorruptDocumentException e = reject(xml);
        return e.defects().getFirst();
    }

    private static CorruptDocumentException reject(String xml) {
        return assertThrows(CorruptDocumentException.class, () -> Xml.read(xml));
    }

    private static List<String> codes(CorruptDocumentException e) {
        return e.defects().stream().map(DocumentDefect::code).toList();
    }
}
