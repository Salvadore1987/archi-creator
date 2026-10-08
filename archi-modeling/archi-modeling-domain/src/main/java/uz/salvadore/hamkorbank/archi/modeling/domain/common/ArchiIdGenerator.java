package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Идентификаторы новых объектов в том виде, в каком их выдаёт Archi:
 * {@code id-} и 32 hex случайного UUID. Импортированные
 * идентификаторы этот класс не трогает.
 */
public final class ArchiIdGenerator {

    private final Supplier<UUID> uuids;

    public ArchiIdGenerator() {
        this(UUID::randomUUID);
    }

    public ArchiIdGenerator(Supplier<UUID> uuids) {
        this.uuids = Objects.requireNonNull(uuids, "uuids");
    }

    public ArchiId next() {
        return ArchiId.of("id-" + uuids.get().toString().replace("-", ""));
    }

    /** Новый идентификатор, не занятый в модели: совпадение — повод взять следующий. */
    public ArchiId nextUnique(Predicate<ArchiId> taken) {
        ArchiId id = next();
        while (taken.test(id)) {
            id = next();
        }
        return id;
    }
}
