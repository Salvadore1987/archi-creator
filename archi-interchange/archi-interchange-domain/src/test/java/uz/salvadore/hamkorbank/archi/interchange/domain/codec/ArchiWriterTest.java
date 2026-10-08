package uz.salvadore.hamkorbank.archi.interchange.domain.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;

class ArchiWriterTest {

    private final ArchiXmlWriter writer = new ArchiXmlWriter();

    private static final String STYLED = """
            <folder name="Views" id="id-f8" type="diagrams">
              <element xsi:type="archimate:ArchimateDiagramModel" name="V" id="id-v1" viewpoint="application_cooperation">
                <child xsi:type="archimate:Group" id="id-g1" name="Внешние системы" fillColor="#F5F6F7" font="1|Segoe UI|9.0|1|WINDOWS|1|-12|0|0|0|700|0|0|0|0|3|2|1|34|Segoe UI" fontColor="#1f2328" lineColor="#5c6773" textAlignment="1">
                  <bounds x="10" y="20" width="400" height="300"/>
                  <feature name="labelExpression" value="${name}"/>
                </child>
              </element>
            </folder>
            """;

    @Test
    @DisplayName("INV-IXC-004: запись одного документа дважды даёт побайтово одно и то же")
    void writingTwiceProducesIdenticalBytes() {
        ModelDocument document = Xml.read(Xml.model(STYLED));

        byte[] first = writer.write(document);
        byte[] second = writer.write(document);
        byte[] afterReread = writer.write(Xml.read(new String(first, StandardCharsets.UTF_8)));

        assertArrayEquals(first, second);
        assertArrayEquals(first, afterReread);
    }

    @Test
    @DisplayName("INV-IXC-004: вывод не зависит от локали")
    void outputDoesNotDependOnLocale() {
        ModelDocument document = Xml.read(Xml.model(STYLED));
        Locale original = Locale.getDefault();
        byte[] inDefault = writer.write(document);
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertArrayEquals(inDefault, writer.write(document));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("раскладка Archi: UTF-8, два пробела, LF, пустой узел закрыт />, текст значения в строку")
    void writesArchiLayout() {
        ModelDocument document = Xml.read(Xml.model("""
                <folder name="Business" id="id-f2" type="business">
                        <element xsi:type="archimate:BusinessActor" name="Клиент" id="id-e1">
                    <documentation>Физлицо</documentation>
                    <property key="source" value="vsdx"/>
                  </element>
                </folder>
                <folder name="Other" id="id-f6" type="other"></folder>
                """));

        assertEquals("""
                <?xml version="1.0" encoding="UTF-8"?>
                <archimate:model xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                xmlns:archimate="http://www.archimatetool.com/archimate" name="Тест" id="id-9c7b5378a397030e3357cb9b" version="5.0.0">
                  <folder name="Business" id="id-f2" type="business">
                    <element xsi:type="archimate:BusinessActor" name="Клиент" id="id-e1">
                      <documentation>Физлицо</documentation>
                      <property key="source" value="vsdx"/>
                    </element>
                  </folder>
                  <folder name="Other" id="id-f6" type="other"/>
                </archimate:model>
                """, new String(writer.write(document), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("FR-21: собственный стиль объекта выходит дословно и на своём месте")
    void ownStyleIsWrittenAsRead() {
        String written = new String(writer.write(Xml.read(Xml.model(STYLED))), StandardCharsets.UTF_8);

        assertTrue(written.contains("<child xsi:type=\"archimate:Group\" id=\"id-g1\" name=\"Внешние системы\" "
                + "fillColor=\"#F5F6F7\" font=\"1|Segoe UI|9.0|1|WINDOWS|1|-12|0|0|0|700|0|0|0|0|3|2|1|34|Segoe UI\" "
                + "fontColor=\"#1f2328\" lineColor=\"#5c6773\" textAlignment=\"1\">"), written);
    }

    @Test
    @DisplayName("FR-03: непрозрачный фрагмент возвращается по адресу без нормализации")
    void rawFragmentIsWrittenAtItsAddress() {
        String written = new String(writer.write(Xml.read(Xml.model(STYLED))), StandardCharsets.UTF_8);

        assertTrue(written.contains(
                "        <bounds x=\"10\" y=\"20\" width=\"400\" height=\"300\"/>\n"
                + "        <feature name=\"labelExpression\" value=\"${name}\"/>\n"
                + "      </child>\n"), written);
    }

    @Test
    @DisplayName("экранирование переживает цикл: кавычки, амперсанд, переводы строк в атрибуте и CR в тексте")
    void escapingSurvivesCycle() {
        ModelDocument document = Xml.read(Xml.model("""
                <folder name="A &amp; B &lt;x&gt; &quot;q&quot;" id="id-f2" type="business">
                  <element xsi:type="archimate:BusinessActor" name="строка&#xA;вторая&#x9;таб" id="id-e1">
                    <documentation>CR&#xD;LF &amp; &lt;тег&gt;</documentation>
                  </element>
                </folder>
                """));

        ModelDocument reread = Xml.read(new String(writer.write(document), StandardCharsets.UTF_8));

        DocumentNode element = reread.find(ArchiId.of("id-e1")).orElseThrow();
        assertEquals(Optional.of("строка\nвторая\tтаб"), element.attribute("name"));
        assertEquals(Optional.of("CR\rLF & <тег>"), element.documentation());
        assertEquals(Optional.of("A & B <x> \"q\""), reread.folders().getFirst().attribute("name"));
    }
}
