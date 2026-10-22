package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossEntry;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossObjectKind;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.LossEntryId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.UuidV7;

class LossReportJsonTest {

    private static final UuidV7 UUIDS = new UuidV7(Clock.fixed(Instant.parse("2026-10-01T09:00:00Z"), ZoneOffset.UTC));

    @Test
    @DisplayName("отчёт пакета картинок: представления в порядке архива, у каждого — файл и свои записи")
    void viewsReport() {
        LossEntry lost = new LossEntry(LossEntryId.next(UUIDS), ArchiId.of("id-e1"), LossObjectKind.ELEMENT,
                "fill", "нет \"градиента\"");
        String json = LossReportJson.viewsReport("PNG", 3, List.of(
                new LossReportJson.ViewLosses("id-v1", "Обзор", "obzor.0a1b2c3d.png", List.of(lost)),
                new LossReportJson.ViewLosses("id-v2", "", "view.0a1b2c3e.png", List.of())));

        assertEquals("{\"format\":\"PNG\",\"sourceVersionNo\":3,\"views\":["
                + "{\"archiId\":\"id-v1\",\"name\":\"Обзор\",\"file\":\"obzor.0a1b2c3d.png\",\"entries\":["
                + "{\"archiId\":\"id-e1\",\"objectKind\":\"" + LossObjectKind.ELEMENT.value()
                + "\",\"lostAspect\":\"fill\",\"reason\":\"нет \\\"градиента\\\"\"}]},"
                + "{\"archiId\":\"id-v2\",\"name\":\"\",\"file\":\"view.0a1b2c3e.png\",\"entries\":[]}]}\n", json);
    }

    @Test
    @DisplayName("архив выгрузки детерминирован: то же содержимое — те же байты, записи в порядке добавления")
    void zipIsDeterministic() throws IOException {
        byte[] first = new DeterministicZip().add("a.csv", new byte[] {1}, new byte[] {2}).add("b.json", "{}"
                .getBytes(StandardCharsets.UTF_8)).bytes();
        byte[] second = new DeterministicZip().add("a.csv", new byte[] {1}, new byte[] {2}).add("b.json", "{}"
                .getBytes(StandardCharsets.UTF_8)).bytes();

        assertArrayEquals(first, second);
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(first))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                names.add(entry.getName());
                if (entry.getName().equals("a.csv")) {
                    assertArrayEquals(new byte[] {1, 2}, zip.readAllBytes());
                }
            }
        }
        assertEquals(List.of("a.csv", "b.json"), names);
    }
}
