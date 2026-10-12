package uz.salvadore.hamkorbank.archi.advisor.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private Instant now = Instant.parse("2026-10-12T09:00:00Z");

    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    };

    @Test
    @DisplayName("FR-52: окно в минуту; отказ называет паузу; отказ в одном окне не занимает место в другом")
    void slidingWindow() {
        RateLimiter limiter = new RateLimiter(clock);
        Map<String, Integer> limits = Map.of("user:a", 2, "workspace:w", 3);

        assertEquals(Optional.empty(), limiter.acquire(limits));
        now = now.plusSeconds(10);
        assertEquals(Optional.empty(), limiter.acquire(limits));
        Optional<Duration> wait = limiter.acquire(limits);
        assertEquals(Optional.of(Duration.ofSeconds(50)), wait);

        assertEquals(Optional.empty(), limiter.acquire(Map.of("user:b", 2, "workspace:w", 3)));
        assertTrue(limiter.acquire(Map.of("user:c", 2, "workspace:w", 3)).isPresent(), "окно пространства полно");

        now = now.plusSeconds(51);
        assertEquals(Optional.empty(), limiter.acquire(limits));
    }
}
