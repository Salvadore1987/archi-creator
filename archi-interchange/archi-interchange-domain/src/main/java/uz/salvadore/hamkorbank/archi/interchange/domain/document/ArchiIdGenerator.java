package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Идентификаторы для новых объектов — в том виде, в каком их выдаёт сам Archi:
 * {@code id-} и 32 hex случайного UUID без дефисов ({@code UUIDFactory.createID},
 * ADR-0002). Импортированные идентификаторы этот класс не трогает никогда.
 *
 * <p>Уникальность гарантируется в пределах модели (INV-MDL-001): совпадение
 * с занятым значением — повод взять следующий, а не ошибка.
 */
public final class ArchiIdGenerator {

    private static final String PREFIX = "id-";

    private final Supplier<UUID> uuids;

    public ArchiIdGenerator() {
        this(UUID::randomUUID);
    }

    ArchiIdGenerator(Supplier<UUID> uuids) {
        this.uuids = Objects.requireNonNull(uuids, "uuids");
    }

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
