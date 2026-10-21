package uz.salvadore.hamkorbank.archi.advisor.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Частота обращений — скользящее окно в минуту, в памяти процесса. Приложение одно,
 * общий счётчик между экземплярами не нужен. Превышение — отказ, а не очередь.
 */
public final class RateLimiter {

    static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Deque<Instant> EMPTY = new ArrayDeque<>(0);

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
        expire(now);
        Duration wait = Duration.ZERO;
        for (Map.Entry<String, Integer> limit : limits.entrySet()) {
            Deque<Instant> window = windows.getOrDefault(limit.getKey(), EMPTY);
            if (window.size() >= limit.getValue()) {
                Duration until = Duration.between(now, window.peekFirst().plus(WINDOW));
                wait = until.compareTo(wait) > 0 ? until : wait;
            }
        }
        if (!wait.isZero()) {
            return Optional.of(wait);
        }
        limits.keySet().forEach(key -> windows.computeIfAbsent(key, k -> new ArrayDeque<>()).addLast(now));
        return Optional.empty();
    }

    /** Ключей с непустым окном — сколько авторов и пространств обращались за последнюю минуту. */
    synchronized int trackedKeys() {
        return windows.size();
    }

    /**
     * Снять из окон обращения старше минуты, а опустевшие окна — целиком: иначе карта росла бы
     * на каждого автора и пространство за всё время жизни процесса.
     */
    private void expire(Instant now) {
        Instant edge = now.minus(WINDOW);
        Iterator<Deque<Instant>> all = windows.values().iterator();
        while (all.hasNext()) {
            Deque<Instant> window = all.next();
            while (!window.isEmpty() && !window.peekFirst().isAfter(edge)) {
                window.pollFirst();
            }
            if (window.isEmpty()) {
                all.remove();
            }
        }
    }
}
