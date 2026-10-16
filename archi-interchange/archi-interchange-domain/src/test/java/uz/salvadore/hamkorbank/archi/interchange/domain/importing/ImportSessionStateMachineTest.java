package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uz.salvadore.hamkorbank.archi.interchange.domain.importing.Sessions.NOW;
import static uz.salvadore.hamkorbank.archi.interchange.domain.importing.Sessions.VALID;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;

class ImportSessionStateMachineTest {

    private static final ModelId MODEL = new ModelId(UUIDUtil.uuid("0192f000-0000-7000-8000-0000000000aa"));

    @Test
    @DisplayName("INV-IXC-002: применить можно только из VALIDATED")
    void applyIsAllowedOnlyFromValidated() {
        ImportSession received = Sessions.received(VALID, false);
        assertThrows(IllegalImportTransitionException.class, () -> received.apply(MODEL, 1, 0, NOW));

        Sessions.parse(received, VALID);
        assertEquals(ImportStatus.PARSED, received.status());
        assertThrows(IllegalImportTransitionException.class, () -> received.apply(MODEL, 1, 0, NOW));

        received.validate(List.of(), NOW);
        ImportAppliedEvent applied = received.apply(MODEL, 1, 0, NOW);

        assertEquals(ImportStatus.APPLIED, received.status());
        assertEquals(MODEL, applied.modelId());
        assertEquals(List.of(applied), received.pullEvents());
        assertEquals(Optional.of(NOW), received.finishedAt());
    }

    @Test
    @DisplayName("INV-IXC-002: повторное применение терминальной сессии отклоняется")
    void applyTwiceIsRejected() {
        ImportSession session = Sessions.received(VALID, false);
        Sessions.parse(session, VALID);
        session.validate(List.of(), NOW);
        session.apply(MODEL, 1, 0, NOW);

        assertThrows(IllegalImportTransitionException.class, () -> session.apply(MODEL, 2, 0, NOW));
    }

    @Test
    @DisplayName("INV-IXC-002: отказ возможен из любого нетерминального состояния и только из него")
    void rejectIsAllowedFromAnyNonTerminal() {
        ImportSession received = Sessions.received(VALID, false);
        received.reject(List.of(), NOW);
        assertEquals(ImportStatus.REJECTED, received.status());
        assertThrows(IllegalImportTransitionException.class, () -> received.reject(List.of(), NOW));

        ImportSession parsed = Sessions.received(VALID, false);
        Sessions.parse(parsed, VALID);
        parsed.reject(List.of(), NOW);
        assertEquals(ImportStatus.REJECTED, parsed.status());

        ImportSession validated = Sessions.received(VALID, false);
        Sessions.parse(validated, VALID);
        validated.validate(List.of(), NOW);
        validated.reject(List.of(), NOW);
        assertEquals(ImportStatus.REJECTED, validated.status());
        assertTrue(validated.pullEvents().isEmpty(), "отклонённый импорт события не порождает");
    }

    @Test
    @DisplayName("переходы не перескакивают: проверка без разбора и разбор дважды отклоняются")
    void transitionsDoNotSkip() {
        ImportSession session = Sessions.received(VALID, false);
        assertThrows(IllegalImportTransitionException.class, () -> session.validate(List.of(), NOW));

        Sessions.parse(session, VALID);
        assertThrows(IllegalImportTransitionException.class, () -> Sessions.parse(session, VALID));
        assertTrue(session.document().isPresent());
    }

    @Test
    @DisplayName("INV-IXC-002: применение без модели или с номером версии меньше единицы не меняет сессию")
    void applyChecksBeforeTransition() {
        ImportSession session = Sessions.received(VALID, false);
        Sessions.parse(session, VALID);
        session.validate(List.of(), NOW);

        assertThrows(RuntimeException.class, () -> session.apply(null, 1, 0, NOW));
        assertThrows(InvalidValueException.class, () -> session.apply(MODEL, 0, 0, NOW));

        assertEquals(ImportStatus.VALIDATED, session.status());
        session.apply(MODEL, 1, 0, NOW);
        assertEquals(ImportStatus.APPLIED, session.status());
    }

    @Test
    @DisplayName("Отказ перехода сессии импорта называет команду и состояние ключами — их переводят")
    void importTransitionRefusalIsTranslatable() {
        ImportSession received = Sessions.received(VALID, false);

        IllegalImportTransitionException e = assertThrows(IllegalImportTransitionException.class,
                () -> received.apply(MODEL, 1, 0, NOW));

        assertEquals(List.of(Message.of(InterchangeMessages.COMMAND_APPLY_IMPORT),
                Message.of(InterchangeMessages.STATUS_IMPORT_RECEIVED)), e.reason().args());
    }
}
