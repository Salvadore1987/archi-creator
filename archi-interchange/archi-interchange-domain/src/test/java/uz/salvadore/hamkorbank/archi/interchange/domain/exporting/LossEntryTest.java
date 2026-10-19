package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
}
