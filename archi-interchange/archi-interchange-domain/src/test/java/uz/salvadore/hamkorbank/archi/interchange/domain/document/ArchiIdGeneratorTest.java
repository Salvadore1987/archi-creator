package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArchiIdGeneratorTest {

    @Test
    @DisplayName("ADR-0002: новый идентификатор — id- и 32 hex, как у UUIDFactory в Archi")
    void generatesArchiFormat() {
        ArchiId id = new ArchiIdGenerator().next();

        assertTrue(id.value().matches("^id-[0-9a-f]{32}$"), id.value());
    }

    @Test
    @DisplayName("подряд выданные идентификаторы различны")
    void consecutiveIdsDiffer() {
        ArchiIdGenerator generator = new ArchiIdGenerator();

        assertNotEquals(generator.next(), generator.next());
    }

    @Test
    @DisplayName("INV-MDL-001: занятый в модели идентификатор пропускается")
    void takenIdIsSkipped() {
        UUID first = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID second = UUID.fromString("00000000-0000-4000-8000-000000000002");
        Iterator<UUID> uuids = List.of(first, second).iterator();
        ArchiIdGenerator generator = new ArchiIdGenerator(uuids::next);

        ArchiId id = generator.nextUnique(Set.of(ArchiId.of("id-00000000000040008000000000000001")));

        assertEquals("id-00000000000040008000000000000002", id.value());
    }
}
