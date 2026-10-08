package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uz.salvadore.hamkorbank.archi.interchange.domain.importing.Sessions.CORRUPT;
import static uz.salvadore.hamkorbank.archi.interchange.domain.importing.Sessions.NOW;
import static uz.salvadore.hamkorbank.archi.interchange.domain.importing.Sessions.VALID;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.DocumentDefect;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;

class StrictImportTest {

    @Test
    @DisplayName("INV-IXC-007: находка ERROR отклоняет импорт только в строгом режиме")
    void errorRejectsOnlyInStrictMode() {
        ImportSession strict = Sessions.received(VALID, true);
        Sessions.parse(strict, VALID);
        strict.validate(List.of(Sessions.matrixViolation()), NOW);

        ImportSession lenient = Sessions.received(VALID, false);
        Sessions.parse(lenient, VALID);
        lenient.validate(List.of(Sessions.matrixViolation()), NOW);

        assertEquals(ImportStatus.REJECTED, strict.status());
        assertEquals(ImportStatus.VALIDATED, lenient.status());
        assertEquals(1, lenient.findingCounts().get(Severity.ERROR), "в мягком режиме нарушение остаётся в отчёте");
        assertEquals(1, strict.findings().size(), "отказ объясняется списком нарушений");
    }

    @Test
    @DisplayName("INV-IXC-007: WARNING не отклоняет и строгий импорт")
    void warningDoesNotRejectEvenInStrictMode() {
        ImportSession strict = Sessions.received(VALID, true);
        Sessions.parse(strict, VALID);
        ImportFinding error = Sessions.matrixViolation();
        strict.validate(List.of(new ImportFinding(error.id(), Severity.WARNING, "IXC_SOMETHING", "мелочь",
                Optional.empty(), Optional.empty())), NOW);

        assertEquals(ImportStatus.VALIDATED, strict.status());
    }

    @ParameterizedTest(name = "strictMode = {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("INV-IXC-007: повреждённый файл отклоняется в обоих режимах, с местом повреждения")
    void corruptedFileIsRejectedInBothModes(boolean strict) {
        ImportSession session = Sessions.received(CORRUPT, strict);

        Sessions.parse(session, CORRUPT);

        assertEquals(ImportStatus.REJECTED, session.status());
        assertTrue(session.document().isEmpty());
        ImportFinding finding = session.findings().getFirst();
        assertEquals(DocumentDefect.DANGLING_REFERENCE, finding.code());
        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(Optional.of(ArchiId.of("id-r1")), finding.archiId());
        assertEquals(Optional.of(8), finding.xmlLine());
    }
}
