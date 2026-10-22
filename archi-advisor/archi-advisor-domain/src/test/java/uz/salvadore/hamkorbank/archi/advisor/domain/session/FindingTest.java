package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorCodes;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.FindingId;

class FindingTest {

    @Test
    @DisplayName("INV-ADV-008: находка без ссылки на объект модели не создаётся")
    void everyFindingHasResolvableTarget() {
        InvalidValueException missing = assertThrows(InvalidValueException.class,
                () -> new Finding(FindingId.next(Sessions.UUIDS), Severity.ERROR, "RELATION_NOT_PERMITTED", "text",
                        TargetKind.RELATIONSHIP, null, null));
        assertEquals(AdvisorMessages.FINDING_WITHOUT_TARGET, missing.reason().key());

        assertThrows(InvalidValueException.class, () -> ElementRef.of("", "name"));
        assertThrows(InvalidValueException.class, () -> ElementRef.of("bad id", "name"));
        assertEquals("id-1", Sessions.finding("id-1").target().archiId());
    }

    @Test
    @DisplayName("Текст находки ограничен, пустое предложение исправления отбрасывается")
    void textIsBounded() {
        assertThrows(InvalidValueException.class,
                () -> new Finding(FindingId.next(Sessions.UUIDS), Severity.INFO, "X", "a".repeat(2001),
                        TargetKind.ELEMENT, ElementRef.of("id-1", "n"), null));
        Finding blank = new Finding(FindingId.next(Sessions.UUIDS), Severity.INFO, "X", "text", TargetKind.ELEMENT,
                ElementRef.of("id-1", "n"), "  ");
        assertNull(blank.suggestion());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("invalidCodes")
    @DisplayName("Код замечания обязателен и не длиннее предела")
    void invalidCodeIsRejected(String code) {
        InvalidValueException refused = assertThrows(InvalidValueException.class,
                () -> new Finding(FindingId.next(Sessions.UUIDS), Severity.INFO, code, "text", TargetKind.ELEMENT,
                        ElementRef.of("id-1", "n"), null));

        assertEquals(AdvisorCodes.INVALID_INPUT, refused.code());
        assertEquals(Message.of(AdvisorMessages.FINDING_CODE_INVALID, Finding.CODE_MAX), refused.reason());
    }

    @Test
    @DisplayName("Совет по исправлению длиннее предела — находка не создаётся")
    void tooLongSuggestionIsRejected() {
        String suggestion = "a".repeat(Finding.TEXT_MAX + 1);

        InvalidValueException refused = assertThrows(InvalidValueException.class,
                () -> new Finding(FindingId.next(Sessions.UUIDS), Severity.INFO, "X", "text", TargetKind.ELEMENT,
                        ElementRef.of("id-1", "n"), suggestion));

        assertEquals(AdvisorCodes.INVALID_INPUT, refused.code());
        assertEquals(Message.of(AdvisorMessages.FINDING_TEXT_INVALID, Finding.TEXT_MAX), refused.reason());
    }

    private static Stream<String> invalidCodes() {
        return Stream.of("   ", "C".repeat(Finding.CODE_MAX + 1));
    }
}
