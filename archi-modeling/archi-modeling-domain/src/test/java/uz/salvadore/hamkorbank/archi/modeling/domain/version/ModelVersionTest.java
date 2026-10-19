package uz.salvadore.hamkorbank.archi.modeling.domain.version;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.event.ModelVersionCommittedEvent;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Models;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

class ModelVersionTest {

    static final String HASH = "a".repeat(64);
    static final ModelId MODEL = ModelId.next(Models.UUIDS);
    static final WorkspaceId WORKSPACE = WorkspaceId.next(Models.UUIDS);

    @Test
    @DisplayName("INV-MDL-010: номера версий строго возрастают и не переиспользуются")
    void versionNumbersAreMonotonic() {
        ModelVersion first = version(null);
        ModelVersion second = version(first);
        ModelVersion third = version(second);

        assertEquals(1, first.versionNo());
        assertEquals(2, second.versionNo());
        assertEquals(3, third.versionNo());
        ModelVersion labelled = second.labelled("AS-IS на аудит");
        assertEquals(2, labelled.versionNo(), "метка не меняет номера");
        assertEquals("AS-IS на аудит", labelled.label());
    }

    @Test
    @DisplayName("ADR-0023: событие о фиксации рождается вместе с версией и описывает её")
    void commitBringsItsEvent() {
        ModelVersion first = version(null);
        ModelVersion.Committed committed = ModelVersion.commit(first, VersionId.next(Models.UUIDS), MODEL,
                WORKSPACE, "architect-1", "  ", Models.NOW, new byte[] {1}, HASH,
                new ModelVersion.ModelSize(12, 7));

        ModelVersionCommittedEvent event = committed.event();
        assertEquals(MODEL, event.modelId());
        assertEquals(WORKSPACE, event.workspaceId());
        assertEquals(committed.version().versionNo(), event.versionNo());
        assertEquals(2, event.versionNo());
        assertEquals("architect-1", event.author());
        assertNull(event.comment(), "пустой комментарий не попадает ни в версию, ни в событие");
        assertEquals(12, event.elementCount());
        assertEquals(7, event.relationshipCount());
        assertEquals(Models.NOW, event.occurredAt());
    }

    @Test
    @DisplayName("FR-47: очищенный снимок без Git — 410, восстанавливать неоткуда")
    void purgedSnapshotIsGone() {
        ModelVersion first = version(null);
        assertArrayEquals(new byte[] {1, 2, 3}, first.requireSnapshot());

        assertThrows(ModelingDomainException.class, () -> first.snapshotPurged(false), "без Git снимок не чистится");
        ModelVersion bound = ModelVersion.restore(first.id(), MODEL, 1, "a", null, null,
                first.createdAt(), first.snapshot(), HASH, "b".repeat(40));
        ModelingDomainException gone = assertThrows(ModelingDomainException.class,
                () -> bound.snapshotPurged(true).requireSnapshot());
        assertEquals(Failure.GONE, gone.failure());
    }

    static ModelVersion version(ModelVersion last) {
        return ModelVersion.commit(last, VersionId.next(Models.UUIDS), MODEL, WORKSPACE, "architect-1",
                "правка", Models.NOW, new byte[] {1, 2, 3}, HASH, new ModelVersion.ModelSize(0, 0))
                .version();
    }

    @Test
    @DisplayName("Снимок версии наружу — копией: правка массива не меняет зафиксированную версию")
    void snapshotDoesNotLeak() {
        ModelVersion version = version(null);

        version.snapshot()[0] = 42;

        assertArrayEquals(new byte[] {1, 2, 3}, version.requireSnapshot());
    }
}
