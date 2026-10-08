package uz.salvadore.hamkorbank.archi.interchange.domain.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-50: повреждённые данные отклоняются — с кодом, объектом и строкой.
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
                  <folder name="Business" id="id-2">
                </archimate:model>
                """);

        assertEquals(DocumentDefect.MALFORMED_XML, defect.code());
        assertEquals(Optional.of(4), defect.line());
        assertTrue(defect.column().isPresent());
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
        assertEquals(DocumentDefect.DANGLING_REFERENCE, defect.code());
        assertEquals(Optional.of("id-r1"), defect.archiId());
        assertEquals(Optional.of(4), defect.line());
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

        assertEquals(List.of(DocumentDefect.DUPLICATE_ID, DocumentDefect.DUPLICATE_ID), codes(e));
        assertEquals(Optional.of("id-e1"), e.defects().getFirst().archiId());
        assertEquals(Optional.of("id-f2"), e.defects().get(1).archiId());
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

        assertEquals(DocumentDefect.MISSING_END, defect.code());
        assertEquals(Optional.of("id-r1"), defect.archiId());
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

        assertEquals(List.of(DocumentDefect.MISSING_ATTRIBUTE, DocumentDefect.INVALID_ID), codes(e));
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

        assertEquals(DocumentDefect.TARGET_CONNECTIONS_MISMATCH, defect.code());
        assertEquals(Optional.of("id-n1"), defect.archiId());
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

        assertEquals(List.of(DocumentDefect.MISSING_END, DocumentDefect.DUPLICATE_ID,
                DocumentDefect.DANGLING_REFERENCE, DocumentDefect.DANGLING_REFERENCE,
                DocumentDefect.DANGLING_REFERENCE), codes(e));
    }

    @Test
    @DisplayName("внешние сущности не раскрываются: файл приходит от пользователя")
    void externalEntitiesAreNotResolved() {
        DocumentDefect defect = single("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE m [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <archimate:model xmlns:archimate="http://www.archimatetool.com/archimate" id="id-1" name="&xxe;"/>
                """);

        assertEquals(DocumentDefect.MALFORMED_XML, defect.code());
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
