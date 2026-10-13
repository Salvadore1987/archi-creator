package uz.salvadore.hamkorbank.archi.interchange.application.oef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OefSchemaTest {

    private static final OefSchema SCHEMA = OefSchema.load();

    private static final String VALID = """
            <?xml version="1.0" encoding="UTF-8"?>
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" identifier="id-m">
              <name xml:lang="ru">Модель</name>
              <elements>
                <element identifier="id-a" xsi:type="ApplicationComponent"><name xml:lang="ru">A</name></element>
              </elements>
              <views>
                <diagrams>
                  <view identifier="id-v" xsi:type="Diagram">
                    <name xml:lang="ru">Вид</name>
                    <node identifier="id-n" xsi:type="Element" elementRef="id-a" x="0" y="0" w="120" h="55"/>
                  </view>
                </diagrams>
              </views>
            </model>
            """;

    @Test
    @DisplayName("FR-43: документ по схеме проходит проверку без обращения в сеть")
    void validDocumentPasses() {
        assertEquals(Optional.empty(), SCHEMA.violation(VALID.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("FR-43: нарушение схемы ловится с местом в документе")
    void violationIsReportedWithPosition() {
        String negative = VALID.replace("x=\"0\"", "x=\"-5\"");
        Optional<String> violation = SCHEMA.violation(negative.getBytes(StandardCharsets.UTF_8));
        assertTrue(violation.isPresent());
        assertTrue(violation.get().startsWith("12:"), violation.get());
    }

    @Test
    @DisplayName("FR-43: ссылка на несуществующий элемент — нарушение ключей схемы")
    void danglingReferenceIsViolation() {
        String dangling = VALID.replace("elementRef=\"id-a\"", "elementRef=\"id-missing\"");
        assertTrue(SCHEMA.violation(dangling.getBytes(StandardCharsets.UTF_8)).isPresent());
    }

    @Test
    @DisplayName("FR-43: тип элемента вне перечня OEF — нарушение")
    void unknownTypeIsViolation() {
        String unknown = VALID.replace("ApplicationComponent", "SketchModel");
        assertTrue(SCHEMA.violation(unknown.getBytes(StandardCharsets.UTF_8)).isPresent());
    }
}
