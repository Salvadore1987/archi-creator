package uz.salvadore.hamkorbank.archi.interchange.domain.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    @DisplayName("версия 7, вариант RFC 9562, в старших битах — миллисекунды")
    void layoutFollowsRfc9562() {
        Instant now = Instant.parse("2026-10-08T09:00:00.123Z");
        UUID uuid = new UuidV7(Clock.fixed(now, ZoneOffset.UTC)).next();

        assertEquals(7, uuid.version());
        assertEquals(2, uuid.variant());
        assertEquals(now.toEpochMilli(), uuid.getMostSignificantBits() >>> 16);
    }

    @Test
    @DisplayName("позднее время даёт больший идентификатор: ключ монотонен")
    void laterTimeSortsAfter() {
        UUID earlier = new UuidV7(Clock.fixed(Instant.parse("2026-10-08T09:00:00Z"), ZoneOffset.UTC)).next();
        UUID later = new UuidV7(Clock.fixed(Instant.parse("2026-10-08T09:00:01Z"), ZoneOffset.UTC)).next();

        assertTrue(Long.compareUnsigned(earlier.getMostSignificantBits(), later.getMostSignificantBits()) < 0);
    }
}
