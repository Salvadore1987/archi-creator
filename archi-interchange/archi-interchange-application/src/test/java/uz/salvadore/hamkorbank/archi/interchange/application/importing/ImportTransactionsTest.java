package uz.salvadore.hamkorbank.archi.interchange.application.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;

class ImportTransactionsTest {

    private static final byte[] FILE = ImportFakes.file("application");

    private final ImportFakes.Transactions transactions = new ImportFakes.Transactions();
    private final ImportFakes.Sessions sessions = new ImportFakes.Sessions();
    private final ImportFakes.Models models = new ImportFakes.Models(transactions);

    @Test
    @DisplayName("Разбор файла идёт вне транзакции, запись модели — в транзакции записи")
    void parsingRunsOutsideTransaction() {
        List<String> parsedIn = new ArrayList<>();
        ArchiDocumentReader reader = in -> {
            parsedIn.add(transactions.current());
            return new StaxArchiDocumentReader().read(in);
        };

        ImportReport report = service(reader).importFile(ImportFakes.ARCHITECT, ImportFakes.WORKSPACE,
                "test.archimate", FILE, "key-1");

        assertEquals(ImportStatus.APPLIED, report.status());
        assertEquals(List.of("none"), parsedIn);
        assertEquals(List.of("write"), models.importedIn);
        assertEquals(1, transactions.opened.stream().filter(t -> t.equals("write")).count(),
                "одна транзакция записи на импорт");
    }

    @Test
    @DisplayName("Повтор ключа, принятый, пока шёл разбор, отдаёт первую попытку, а не вторую модель")
    void keyTakenWhileParsingIsReplayed() {
        ArchiDocumentReader reader = new ArchiDocumentReader() {
            @Override
            public ModelDocument read(InputStream in) {
                ModelDocument document = new StaxArchiDocumentReader().read(in);
                ImportReport first = service(new StaxArchiDocumentReader()).importFile(ImportFakes.ARCHITECT,
                        ImportFakes.WORKSPACE, "test.archimate", FILE, "key-2");
                assertEquals(ImportStatus.APPLIED, first.status());
                return document;
            }
        };

        ImportReport second = service(reader).importFile(ImportFakes.ARCHITECT, ImportFakes.WORKSPACE,
                "test.archimate", FILE, "key-2");

        assertTrue(second.replayed());
        assertEquals(1, models.importedIn.size());
        assertEquals(1, sessions.rows.size());
    }

    private ImportService service(ArchiDocumentReader reader) {
        return ImportFakes.service(reader, transactions, sessions, models);
    }
}
