package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Идентификаторы для новых объектов — в том виде, в каком их выдаёт сам Archi:
 * {@code id-} и 32 hex UUID без дефисов, как {@code UUIDFactory.createID}. UUID — версии 7
 * от генератора приложения: время в нём идёт по часам приложения.
 * Импортированные идентификаторы этот класс не трогает никогда.
 *
 * <p>Уникальность гарантируется в пределах модели: совпадение
 * с занятым значением — повод взять следующий, а не ошибка.
 */
public final class ArchiIdGenerator {

    private static final String PREFIX = "id-";

    private final Supplier<UUID> uuids;

    /** Генератор поверх UUID приложения — {@code UuidV7::next} своего контекста. */
    public ArchiIdGenerator(Supplier<UUID> uuids) {
        this.uuids = Objects.requireNonNull(uuids, "uuids");
    }

    /** Новый идентификатор в формате Archi. */
    public ArchiId next() {
        return ArchiId.of(PREFIX + uuids.get().toString().replace("-", ""));
    }

    /** Новый идентификатор, которого нет среди уже занятых в модели. */
    public ArchiId nextUnique(Set<ArchiId> taken) {
        ArchiId id = next();
        while (taken.contains(id)) {
            id = next();
        }
        return id;
    }
}
