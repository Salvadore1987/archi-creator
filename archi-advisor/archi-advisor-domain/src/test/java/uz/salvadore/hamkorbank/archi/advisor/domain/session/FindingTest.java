package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.FindingId;

class FindingTest {

    @Test
    @DisplayName("INV-ADV-008: находка без ссылки на объект модели не создаётся")
    void everyFindingHasResolvableTarget() {
        InvalidValueException missing = assertThrows(InvalidValueException.class,
                () -> new Finding(FindingId.next(Sessions.UUIDS), Severity.ERROR, "RELATION_NOT_PERMITTED", "text",
                        TargetKind.RELATIONSHIP, null, Optional.empty()));
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
                        TargetKind.ELEMENT, ElementRef.of("id-1", "n"), Optional.empty()));
        Finding blank = new Finding(FindingId.next(Sessions.UUIDS), Severity.INFO, "X", "text", TargetKind.ELEMENT,
                ElementRef.of("id-1", "n"), Optional.of("  "));
        assertEquals(Optional.empty(), blank.suggestion());
    }
}
