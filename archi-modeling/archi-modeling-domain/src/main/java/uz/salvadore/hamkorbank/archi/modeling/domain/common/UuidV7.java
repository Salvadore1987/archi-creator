package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.fasterxml.uuid.UUIDClock;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * UUID версии 7 (RFC 9562): 48 бит миллисекунд Unix-времени, затем счётчик и случайные
 * биты. Генерируется приложением, а не базой: монотонен по времени, поэтому первичный
 * ключ на нём не дробит индекс.
 *
 * <p>Значение собирает java-uuid-generator — собственной сборки битов в проекте нет.
 * Класс лишь отдаёт библиотеке часы приложения: тесты фиксируют время через {@link Clock},
 * а в пределах одной миллисекунды библиотека сохраняет порядок счётчиком.
 *
 * <p>Своя обёртка, а не общая с interchange и advisor: доменные модули друг от друга не зависят.
 */
public final class UuidV7 {

    private final NoArgGenerator generator;

    public UuidV7(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        this.generator = Generators.timeBasedEpochGenerator(null, new UUIDClock() {
            @Override
            public long currentTimeMillis() {
                return clock.millis();
            }
        });
    }

    /** Генератор на системных часах в UTC — для кода вне тестов. */
    public static UuidV7 systemUtc() {
        return new UuidV7(Clock.systemUTC());
    }

    /** Следующий идентификатор версии 7 по часам генератора. */
    public UUID next() {
        return generator.generate();
    }
}
