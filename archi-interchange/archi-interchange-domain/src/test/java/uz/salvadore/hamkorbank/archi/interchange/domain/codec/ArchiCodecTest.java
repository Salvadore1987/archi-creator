package uz.salvadore.hamkorbank.archi.interchange.domain.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;

class ArchiCodecTest {

    private static final String RICH = """
            <folder name="Strategy" id="id-f1" type="strategy">
              <folder name="Capability map" id="id-f11">
                <element xsi:type="archimate:Capability" name="KYC" id="id-e1">
                  <property key="source" value="vsdx"/>
                  <property key="owner"/>
                </element>
              </folder>
            </folder>
            <folder name="Application" id="id-f3" type="application">
              <element xsi:type="archimate:ApplicationComponent" name="ABS" id="id-e2">
                <documentation>Первая строка
            вторая</documentation>
              </element>
              <element xsi:type="archimate:FutureElement" name="Из будущего Archi" id="id-e3" futureAttr="1"/>
            </folder>
            <folder name="Relations" id="id-f7" type="relations">
              <element xsi:type="archimate:AccessRelationship" id="id-r1" source="id-e2" target="id-e1" accessType="1"/>
            </folder>
            <folder name="Views" id="id-f8" type="diagrams">
              <element xsi:type="archimate:ArchimateDiagramModel" name="V" id="id-v1">
                <child xsi:type="archimate:Group" id="id-g1" name="G" fillColor="#F5F6F7">
                  <bounds x="10" y="20" width="400" height="300"/>
                  <child xsi:type="archimate:DiagramObject" id="id-d1" targetConnections="id-c1" archimateElement="id-e1">
                    <bounds x="20" y="60" width="150" height="60"/>
                  </child>
                </child>
                <child xsi:type="archimate:DiagramObject" id="id-d2" archimateElement="id-e2">
                  <bounds x="500" y="60" width="150" height="60"/>
                  <sourceConnection xsi:type="archimate:Connection" id="id-c1" source="id-d2" target="id-d1" archimateRelationship="id-r1">
                    <bendpoint startX="10" startY="-5" endX="-40" endY="0"/>
                  </sourceConnection>
                </child>
                <child xsi:type="archimate:Note" id="id-n1">
                  <bounds x="0" y="400" width="185" height="80"/>
                  <content>Заметка</content>
                </child>
              </element>
            </folder>
            <purpose>Описание</purpose>
            <metadata><entry key="k" value="v"/></metadata>
            """;

    @Test
    @DisplayName("INV-IXC-005: документ переживает цикл записи и чтения без изменений")
    void documentSurvivesWriteReadCycle() {
        ModelDocument document = Xml.read(Xml.model(RICH));

        ModelDocument cycled = Xml.read(new String(new ArchiXmlWriter().write(document), StandardCharsets.UTF_8));

        assertEquals(document, cycled);
    }
}
