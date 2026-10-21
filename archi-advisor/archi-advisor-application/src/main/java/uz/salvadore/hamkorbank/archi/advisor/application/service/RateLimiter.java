package uz.salvadore.hamkorbank.archi.advisor.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Частота обращений — скользящее окно в минуту, в памяти процесса. Приложение одно,
 * общий счётчик между экземплярами не нужен. Превышение — отказ, а не очередь.
 */
public final class RateLimiter {

    static final Duration WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final Map<String, Deque<Instant>> windows = new HashMap<>();

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Занять место в окнах всех ключей разом — или не занять ни в одном.
     *
     * @param limits ключ окна → допустимое число обращений в минуту
     * @return пусто — допущено; иначе — через сколько повторить
     */
    public synchronized Optional<Duration> acquire(Map<String, Integer> limits) {
        Instant now = clock.instant();
        Duration wait = Duration.ZERO;
        for (Map.Entry<String, Integer> limit : limits.entrySet()) {
            Deque<Instant> window = windows.computeIfAbsent(limit.getKey(), k -> new ArrayDeque<>());
            while (!window.isEmpty() && !window.peekFirst().isAfter(now.minus(WINDOW))) {
                window.pollFirst();
            }
            if (window.size() >= limit.getValue()) {
                Duration until = Duration.between(now, window.peekFirst().plus(WINDOW));
                wait = until.compareTo(wait) > 0 ? until : wait;
            }
        }
        if (!wait.isZero()) {
            return Optional.of(wait);
        }
        limits.keySet().forEach(key -> windows.get(key).addLast(now));
        return Optional.empty();
    }
}
