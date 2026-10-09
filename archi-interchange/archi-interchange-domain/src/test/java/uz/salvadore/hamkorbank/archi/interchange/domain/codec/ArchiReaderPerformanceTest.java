package uz.salvadore.hamkorbank.archi.interchange.domain.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;

/**
 * Импорт файла на 5 000 строк XML — не дольше 3 с на сервере.
 *
 * <p>Замер холодный, первым же чтением в JVM: так приходит реальный импорт.
 * Запас большой намеренно — тест ловит деградацию на порядок (квадратичный
 * обход, DOM вместо потока), а не шум планировщика CI.
 */
class ArchiReaderPerformanceTest {

    private static final Duration LIMIT = Duration.ofSeconds(3);

    @Test
    @DisplayName("NFR-02: файл на 5 000+ строк читается не дольше 3 с")
    void fiveThousandLinesAreReadWithinThreeSeconds() {
        String xml = Xml.model(content(800));
        long lines = xml.lines().count();
        assertTrue(lines >= 5_000, "файл короче нормы: " + lines + " строк");

        long started = System.nanoTime();
        ModelDocument document = Xml.read(xml);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertEquals(800, document.elements().size());
        assertTrue(elapsed.compareTo(LIMIT) <= 0, lines + " строк прочитаны за " + elapsed.toMillis() + " мс");
    }

    /** Пропорции эталонной модели: элементы со свойствами, связи, представление с соединениями. */
    private static String content(int elements) {
        StringBuilder xml = new StringBuilder();
        xml.append("<folder name=\"Application\" id=\"id-fa\" type=\"application\">\n");
        for (int i = 0; i < elements; i++) {
            xml.append("""
                      <element xsi:type="archimate:ApplicationComponent" name="Система %1$d" id="id-e%1$d">
                        <documentation>Описание системы %1$d</documentation>
                        <property key="source" value="vsdx"/>
                      </element>
                    """.formatted(i));
        }
        xml.append("</folder>\n<folder name=\"Relations\" id=\"id-fr\" type=\"relations\">\n");
        for (int i = 1; i < elements; i++) {
            xml.append("  <element xsi:type=\"archimate:FlowRelationship\" id=\"id-r%d\" source=\"id-e%d\" target=\"id-e%d\"/>\n"
                    .formatted(i, i - 1, i));
        }
        xml.append("</folder>\n<folder name=\"Views\" id=\"id-fv\" type=\"diagrams\">\n")
                .append("  <element xsi:type=\"archimate:ArchimateDiagramModel\" name=\"Все\" id=\"id-v\">\n");
        for (int i = 0; i < elements; i++) {
            String target = i == 0 ? "" : " targetConnections=\"id-c" + i + "\"";
            xml.append("    <child xsi:type=\"archimate:DiagramObject\" id=\"id-d%d\"%s archimateElement=\"id-e%d\">\n"
                    .formatted(i, target, i));
            xml.append("      <bounds x=\"%d\" y=\"%d\" width=\"120\" height=\"55\"/>\n".formatted(i % 20 * 140, i / 20 * 80));
            if (i + 1 < elements) {
                xml.append("      <sourceConnection xsi:type=\"archimate:Connection\" id=\"id-c%d\" source=\"id-d%d\" "
                        .formatted(i + 1, i) + "target=\"id-d%d\" archimateRelationship=\"id-r%d\"/>\n".formatted(i + 1, i + 1));
            }
            xml.append("    </child>\n");
        }
        return xml.append("  </element>\n</folder>\n").toString();
    }
}
