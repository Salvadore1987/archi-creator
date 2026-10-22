package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.LossEntryId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.UuidV7;

class LossEntryTest {

    private static final UuidV7 UUIDS = new UuidV7(Clock.fixed(Instant.parse("2026-10-08T09:00:00Z"),
            ZoneOffset.UTC));

    @Test
    @DisplayName("вид объекта потери — из закрытого списка, на проводе строчными")
    void kindHasWireValue() {
        assertEquals(LossObjectKind.RELATIONSHIP, LossObjectKind.of("relationship"));
        assertEquals("element", LossObjectKind.ELEMENT.value());
    }

    @Test
    @DisplayName("неизвестный или пустой вид объекта — отказ значения, а не NPE")
    void unknownKindIsRejected() {
        assertThrows(InvalidValueException.class, () -> LossObjectKind.of("widget"));
        assertThrows(InvalidValueException.class, () -> LossObjectKind.of(null));
    }

    @Test
    @DisplayName("запись потери без вида объекта не строится")
    void entryRequiresKind() {
        assertThrows(NullPointerException.class, () -> new LossEntry(LossEntryId.next(UUIDS), ArchiId.of("id-e1"),
                null, "raw_xml", "reason"));
    }

    @ParameterizedTest
    @CsvSource({"' ', причина", "raw_xml, ' '", "'', ''"})
    @DisplayName("запись потери без потерянной части или без причины не строится")
    void entryRequiresAspectAndReason(String lostAspect, String reason) {
        InvalidValueException e = assertThrows(InvalidValueException.class, () -> new LossEntry(
                LossEntryId.next(UUIDS), ArchiId.of("id-e1"), LossObjectKind.ELEMENT, lostAspect, reason));

        assertEquals(InterchangeCodes.INVALID_REQUEST, e.code());
        assertEquals(InterchangeMessages.LOSS_WITHOUT_REASON, e.reason().key());
    }
}
