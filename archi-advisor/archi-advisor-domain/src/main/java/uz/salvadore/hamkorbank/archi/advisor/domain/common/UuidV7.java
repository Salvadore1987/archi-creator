package uz.salvadore.hamkorbank.archi.advisor.domain.common;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * UUID версии 7 (RFC 9562): 48 бит миллисекунд Unix-времени, затем случайные биты.
 * Генерируется приложением, а не базой.
 *
 * <p>Своя копия, а не общая с modeling: доменные модули друг от друга не зависят.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Clock clock;

    public UuidV7(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public UUID next() {
        long millis = clock.millis();
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        long high = (millis & 0xFFFF_FFFF_FFFFL) << 16
                | 0x7000L
                | ((random[0] & 0x0FL) << 8) | (random[1] & 0xFFL);
        long low = 0x8000_0000_0000_0000L | ((random[2] & 0x3FL) << 56);
        for (int i = 3; i < 10; i++) {
            low |= (random[i] & 0xFFL) << (8 * (9 - i));
        }
        return new UUID(high, low);
    }
}
