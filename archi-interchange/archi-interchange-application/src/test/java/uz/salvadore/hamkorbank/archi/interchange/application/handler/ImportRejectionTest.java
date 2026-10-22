package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.ImportArchimateFileCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportArchimateFileResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportArchimateFileResponse.Outcome;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportFindingResponse;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
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
    private final ImportFakes.Policy policy = new ImportFakes.Policy();
    private final ArchimateFileImportCommandHandler handler =
            ImportFakes.handler(new StaxArchiDocumentReader(), transactions, sessions, models, policy);

    @Test
    @DisplayName("INV-IXC-007: модель из файла не собирается — сессия отклонена как повреждённый файл")
    void unbuildableFileIsRejectedAsCorrupt() {
        byte[] file = ImportFakes.file("неизвестный-тип");

        ImportArchimateFileResponse report = importFile(file, "key-1");
        ImportArchimateFileResponse replay = importFile(file, "key-1");

        assertEquals(ImportStatus.REJECTED.name(), report.status());
        assertEquals(Outcome.REJECTED_CORRUPT, report.outcome());
        assertEquals(InterchangeCodes.CORRUPT_DOCUMENT, report.refusalCode());
        assertEquals(InterchangeMessages.REJECTED_CORRUPT, report.refusalText());
        ImportFindingResponse finding = report.findings().getFirst();
        assertEquals(InterchangeCodes.CORRUPT_DOCUMENT, finding.code());
        assertEquals(InterchangeMessages.IMPORT_CONTENT_REJECTED, finding.message());
        assertEquals(List.of(), models.importedIn);
        assertEquals(1, sessions.rows.size());
        assertTrue(replay.replayed());
        assertEquals(Outcome.REJECTED_CORRUPT, replay.outcome(), "повтор отклонённой — тот же отказ");
        assertEquals(report.sessionId(), replay.sessionId());
    }

    @Test
    @DisplayName("INV-IXC-007: modeling не собирает модель из строк — повреждённый файл в отчёте, записи нет")
    void rowsRefusedByModelingAreCorrupt() {
        models.verifyRefusal = new ModelingDomainException("MDL_TEST", Failure.UNPROCESSABLE,
                Message.of("modeling.test"));

        ImportArchimateFileResponse report = importFile(ImportFakes.file("application"), "key-5");

        assertEquals(Outcome.REJECTED_CORRUPT, report.outcome());
        assertEquals(InterchangeCodes.CORRUPT_DOCUMENT, report.findings().getFirst().code());
        assertEquals(List.of(), models.importedIn, "до записи не дошло");
    }

    @Test
    @DisplayName("FR-49: строгий режим и нарушение матрицы — исход «отклонён строгостью» со своим кодом")
    void strictRejectionHasItsOwnOutcome() {
        policy.strict = true;

        ImportArchimateFileResponse report = importFile(ImportFakes.withMatrixViolation(), "key-3");

        assertEquals(Outcome.REJECTED_STRICT, report.outcome());
        assertEquals(InterchangeCodes.STRICT_IMPORT_REJECTED, report.refusalCode());
        assertEquals(InterchangeMessages.REJECTED_STRICT, report.refusalText());
        assertNull(report.modelId());
        assertNull(report.versionNo());
    }

    @Test
    @DisplayName("Принятый файл — модель и первая версия, без кода отказа")
    void appliedFileHasModelAndNoRefusal() {
        ImportArchimateFileResponse report = importFile(ImportFakes.file("application"), "key-4");

        assertEquals(Outcome.APPLIED, report.outcome());
        assertEquals(1L, report.versionNo());
        assertNull(report.refusalCode());
        assertNull(report.refusalText());
    }

    @Test
    @DisplayName("Пространство и имя файла не заданы — сценарий подставляет свои")
    void missingWorkspaceAndNameAreFilledIn() {
        handler.handle(ImportFakes.ARCHITECT,
                new ImportArchimateFileCommand(null, " ", ImportFakes.file("application"), null));

        ImportSession session = sessions.rows.values().iterator().next();
        assertEquals(ImportFakes.WORKSPACE, session.workspaceId().value());
        assertEquals("model.archimate", session.sourceName());
    }

    @Test
    @DisplayName("Отказ записи модели — отказ клиенту и отклонённая сессия после отката")
    void refusedWriteLeavesRejectedSession() {
        ModelingDomainException refusal = new ModelingDomainException("MDL_TEST", Failure.CONFLICT,
                Message.of("modeling.test"));
        models.refusal = refusal;

        ModelingDomainException thrown = assertThrows(ModelingDomainException.class,
                () -> importFile(ImportFakes.file("application"), "key-2"));

        assertSame(refusal, thrown);
        assertEquals(List.of("write"), models.importedIn, "модель записывалась до отказа");
        assertEquals(List.of(), models.stored, "запись модели откатилась вместе с транзакцией");
        assertEquals(1, sessions.rows.size(), "отклонённая сессия пережила откат");
        ImportSession session = sessions.rows.values().iterator().next();
        assertEquals(ImportStatus.REJECTED, session.status());
        assertEquals("MDL_TEST", session.findings().getLast().code());
        assertEquals(List.of("write", "write"), transactions.opened.stream()
                .filter(t -> t.equals("write")).toList(), "запись сессии — отдельной транзакцией");
    }

    private ImportArchimateFileResponse importFile(byte[] file, String key) {
        return handler.handle(ImportFakes.ARCHITECT,
                new ImportArchimateFileCommand(ImportFakes.WORKSPACE, "x.archimate", file, key));
    }
}
