package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ExportJobId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.LossEntryId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.UuidV7;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

class ExportJobTest {

    private static final Instant NOW = Instant.parse("2026-10-08T09:00:00Z");
    private static final UuidV7 UUIDS = new UuidV7(Clock.fixed(NOW, ZoneOffset.UTC));
    private static final Artifact FILE = Artifact.of("<x/>".getBytes(StandardCharsets.UTF_8),
            "application/xml", "model.archimate");

    @Test
    @DisplayName("INV-IXC-006: выгрузка без отчёта о потерях не переходит в RENDERED")
    void renderedJobAlwaysHasLossReport() {
        ExportJob job = job(ExportFormat.OPEN_EXCHANGE, 7);

        ExportRuleViolationException e = assertThrows(ExportRuleViolationException.class,
                () -> job.render(7, FILE, null));

        assertEquals("INV-IXC-006", e.invariant());
        assertEquals(ExportStatus.REQUESTED, job.status());

        job.render(7, FILE, new LossReport(List.of(loss()), true));
        job.deliver();
        assertEquals(ExportStatus.DELIVERED, job.status());
        assertTrue(job.lossReport().isPresent());
    }

    @Test
    @DisplayName("INV-IXC-006: у .archimate отчёт обязан быть пуст — пустой отчёт валиден")
    void archimateExportMustBeLossless() {
        ExportJob lossy = job(ExportFormat.ARCHIMATE, 3);
        assertThrows(ExportRuleViolationException.class,
                () -> lossy.render(3, FILE, new LossReport(List.of(loss()), true)));

        ExportJob clean = job(ExportFormat.ARCHIMATE, 3);
        clean.render(3, FILE, LossReport.lossless());
        assertEquals(ExportStatus.RENDERED, clean.status());
    }

    @Test
    @DisplayName("INV-IXC-008: выгружается версия, зафиксированная при запросе, а не текущая")
    void exportPinsSourceVersion() {
        ExportJob job = job(ExportFormat.SVG, 4);

        ExportRuleViolationException e = assertThrows(ExportRuleViolationException.class,
                () -> job.render(5, FILE, LossReport.lossless()));

        assertEquals("INV-IXC-008", e.invariant());
        assertEquals(4, job.sourceVersionNo());
    }

    @Test
    @DisplayName("сбой возможен из нетерминального состояния, но не после доставки")
    void failIsAllowedOnlyBeforeDelivery() {
        ExportJob failed = job(ExportFormat.PNG, 1);
        failed.fail("Batik упал");
        assertEquals(Optional.of("Batik упал"), failed.failureReason());

        ExportJob delivered = job(ExportFormat.ARCHIMATE, 1);
        delivered.render(1, FILE, LossReport.lossless());
        delivered.deliver();
        assertThrows(IllegalStateException.class, () -> delivered.fail("поздно"));
    }

    @Test
    @DisplayName("отпечаток артефакта считается по содержимому")
    void artifactHashMatchesContent() {
        assertThrows(IllegalArgumentException.class, () -> new Artifact(new byte[] {1}, "application/xml", "a",
                Artifact.of(new byte[] {2}, "application/xml", "a").hash()));
    }

    private static ExportJob job(ExportFormat format, long version) {
        return ExportJob.request(ExportJobId.next(UUIDS), new WorkspaceId(UUID.randomUUID()),
                new ModelId(UUID.randomUUID()), Optional.empty(), format, ExportOptions.none(), version,
                "architect", NOW);
    }

    private static LossEntry loss() {
        return new LossEntry(LossEntryId.next(UUIDS), ArchiId.of("id-e1"), "element", "raw_xml",
                "в OEF нет места для непрозрачных узлов");
    }
}
