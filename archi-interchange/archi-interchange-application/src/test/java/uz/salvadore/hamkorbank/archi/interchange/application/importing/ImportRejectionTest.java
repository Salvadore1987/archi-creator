package uz.salvadore.hamkorbank.archi.interchange.application.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportFinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportSession;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;

/** Отказ после разбора — тоже итог сессии: в журнале, с находкой, повтор ключа отдаёт его же. */
class ImportRejectionTest {

    private final ImportFakes.Transactions transactions = new ImportFakes.Transactions();
    private final ImportFakes.Sessions sessions = new ImportFakes.Sessions();
    private final ImportFakes.Models models = new ImportFakes.Models(transactions);
    private final ImportService service =
            ImportFakes.service(new StaxArchiDocumentReader(), transactions, sessions, models);

    @Test
    @DisplayName("INV-IXC-007: модель из файла не собирается — сессия отклонена как повреждённый файл")
    void unbuildableFileIsRejectedAsCorrupt() {
        byte[] file = ImportFakes.file("неизвестный-тип");

        ImportReport report = service.importFile(ImportFakes.ARCHITECT, ImportFakes.WORKSPACE, "x.archimate",
                file, "key-1");
        ImportReport replay = service.importFile(ImportFakes.ARCHITECT, ImportFakes.WORKSPACE, "x.archimate",
                file, "key-1");

        assertEquals(ImportStatus.REJECTED, report.status());
        assertTrue(report.rejectedAsCorrupt());
        ImportFinding finding = report.findings().getFirst();
        assertEquals(InterchangeCodes.CORRUPT_DOCUMENT, finding.code());
        assertEquals(InterchangeMessages.IMPORT_CONTENT_REJECTED, finding.message().key());
        assertEquals(List.of(), models.importedIn);
        assertEquals(1, sessions.rows.size());
        assertTrue(replay.replayed());
        assertEquals(report.sessionId(), replay.sessionId());
    }

    @Test
    @DisplayName("Отказ записи модели — отказ клиенту и отклонённая сессия после отката")
    void refusedWriteLeavesRejectedSession() {
        ModelingDomainException refusal = new ModelingDomainException("MDL_TEST", Failure.CONFLICT,
                Message.of("modeling.test"));
        models.refusal = refusal;

        ModelingDomainException thrown = assertThrows(ModelingDomainException.class, () -> service.importFile(
                ImportFakes.ARCHITECT, ImportFakes.WORKSPACE, "x.archimate", ImportFakes.file("application"),
                "key-2"));

        assertSame(refusal, thrown);
        ImportSession session = sessions.rows.values().iterator().next();
        assertEquals(ImportStatus.REJECTED, session.status());
        assertEquals("MDL_TEST", session.findings().getLast().code());
        assertEquals(List.of("write", "write"), transactions.opened.stream()
                .filter(t -> t.equals("write")).toList(), "запись сессии — отдельной транзакцией");
    }
}
