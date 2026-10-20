package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

class CatalogCsvWriterTest {

    /** Значения «да/нет» — как их отдаст каталог сообщений на русском. */
    private static final TextCatalog RU = message -> message.key().equals(InterchangeMessages.CSV_YES) ? "да" : "нет";

    private static final Path FIXTURES = Path.of("../../archi-bootstrap/src/test/resources/fixtures");
    private static final Path REFERENCE = Path.of("../../docs/Hamkorbank_AS_IS_strict.archimate");

    @Test
    @DisplayName("FR-45: два файла, UTF-8 с BOM, разделитель ';', типы и слои по-человечески")
    void catalogHasTwoFilesWithBom() throws IOException {
        Map<String, byte[]> files = unzip(new CatalogCsvWriter(RU).write(
                Files.readAllBytes(FIXTURES.resolve("all_relationship_types.archimate")),
                CatalogCsvOptions.of(null, null)));

        assertEquals(List.of("elements.csv", "relations.csv"), List.copyOf(files.keySet()));
        assertArrayEquals(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                Arrays.copyOf(files.get("elements.csv"), 3));
        String elements = text(files.get("elements.csv"));
        assertTrue(elements.startsWith("id;type;name;layer;folder;documentation;views;placed\r\n"), elements);
        assertTrue(elements.contains(";Application Component;Платёжный шлюз;Application;Application;;1;да\r\n"),
                elements);
        String relations = text(files.get("relations.csv"));
        assertTrue(relations.contains(";Serving;id-9fb01774961a65c58ae7ee4a5aa78f7d;Приём платежей;"), relations);
        assertEquals(17 + 1, relations.split("\r\n").length, "17 связей и заголовок");
    }

    @Test
    @DisplayName("§5.2: столбцы свойств — по частоте ключа; фильтр папкой; sep=','")
    void propertyColumnsByFrequencyAndFolderFilter() throws IOException {
        byte[] reference = Files.readAllBytes(REFERENCE);
        String all = text(unzip(new CatalogCsvWriter(RU).write(reference,
                CatalogCsvOptions.of(",", null))).get("elements.csv"));
        String header = all.substring(0, all.indexOf("\r\n"));
        assertTrue(header.startsWith("id,type,name,layer,folder,documentation,views,placed,"), header);

        String applicationFolder = "id-" + firstFolderId(reference, "application");
        String filtered = text(unzip(new CatalogCsvWriter(RU).write(reference,
                CatalogCsvOptions.of(null, applicationFolder))).get("elements.csv"));
        assertTrue(filtered.contains(";Application;"), "поддерево Application на месте");
        assertTrue(!filtered.contains(";Business;") && !filtered.contains(";Technology;"),
                "чужие корни отфильтрованы");
        assertTrue(filtered.length() < all.length());
    }

    @Test
    @DisplayName("§5.2: папки нет в версии или это не папка — отказ 404, а не пустой каталог")
    void unknownFolderIsRejected() throws IOException {
        byte[] reference = Files.readAllBytes(REFERENCE);
        String element = "id-" + firstElementId(reference);
        for (String folder : List.of("id-00000000000000000000000000000000", element)) {
            InterchangeException failure = assertThrows(InterchangeException.class,
                    () -> new CatalogCsvWriter(RU).write(reference, CatalogCsvOptions.of(null, folder)));
            assertEquals(InterchangeCodes.FOLDER_NOT_IN_VERSION, failure.code(), folder);
            assertEquals(Failure.NOT_FOUND, failure.failure(), folder);
            assertEquals(Map.of("folderArchiId", folder), failure.details(), folder);
        }
    }

    @Test
    @DisplayName("§5.2: значение с ведущим =, +, -, @ — с апострофом, Excel не выполнит его как формулу")
    void formulaPrefixIsEscaped() throws IOException {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <archimate:model xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                xmlns:archimate="http://www.archimatetool.com/archimate" name="m" \
                id="id-08d90f89945db916e45a0ffddcef01b8" version="5.0.0">
                  <folder name="Business" id="id-26155a74b0630c3744cf809f15e023ec" type="business">
                    <element xsi:type="archimate:BusinessActor" name="=HYPERLINK(&quot;x&quot;)" \
                id="id-9e3f08b2827e0cfe7cbf274d9a493294">
                      <documentation>-1+2</documentation>
                      <property key="@owner" value="+7 71 200"/>
                      <property key="owner" value="Отдел - платежи"/>
                    </element>
                  </folder>
                </archimate:model>
                """;
        String elements = text(unzip(new CatalogCsvWriter(RU).write(xml.getBytes(StandardCharsets.UTF_8),
                CatalogCsvOptions.of(null, null))).get("elements.csv"));

        String[] lines = elements.split("\r\n");
        assertTrue(lines[0].endsWith(";placed;'@owner;owner"), lines[0]);
        assertEquals("id-9e3f08b2827e0cfe7cbf274d9a493294;Business Actor;\"'=HYPERLINK(\"\"x\"\")\";Business;"
                + "Business;'-1+2;0;нет;'+7 71 200;Отдел - платежи", lines[1]);
    }

    @Test
    @DisplayName("§5.2: табуляция и возврат каретки в начале — тоже начало формулы; в середине — нет")
    void neutralizeLeadingOnly() {
        assertEquals("'\t=1", CatalogCsvWriter.neutralize("\t=1"));
        assertEquals("'\r=1", CatalogCsvWriter.neutralize("\r=1"));
        assertEquals("a=1", CatalogCsvWriter.neutralize("a=1"));
        assertEquals("", CatalogCsvWriter.neutralize(""));
    }

    @Test
    @DisplayName("RFC 4180: поле с разделителем, кавычкой или переводом строки — в кавычках")
    void quoting() {
        assertEquals("\"a;b\"", CatalogCsvWriter.quote("a;b", ';'));
        assertEquals("\"он сказал \"\"да\"\"\"", CatalogCsvWriter.quote("он сказал \"да\"", ';'));
        assertEquals("\"строка\nвторая\"", CatalogCsvWriter.quote("строка\nвторая", ';'));
        assertEquals("a,b", CatalogCsvWriter.quote("a,b", ';'));
    }

    private static String firstElementId(byte[] xml) {
        String text = new String(xml, StandardCharsets.UTF_8);
        int at = text.indexOf("xsi:type=\"archimate:ApplicationComponent\"");
        int idStart = text.indexOf("id=\"id-", at) + 7;
        return text.substring(idStart, text.indexOf('"', idStart));
    }

    private static String firstFolderId(byte[] xml, String type) {
        String text = new String(xml, StandardCharsets.UTF_8);
        int at = text.indexOf("type=\"" + type + "\"");
        int idStart = text.lastIndexOf("id=\"id-", at) + 7;
        return text.substring(idStart, text.indexOf('"', idStart));
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                files.put(entry.getName(), in.readAllBytes());
            }
        }
        return files;
    }

    private static String text(byte[] bom) {
        return new String(bom, 3, bom.length - 3, StandardCharsets.UTF_8);
    }
}
