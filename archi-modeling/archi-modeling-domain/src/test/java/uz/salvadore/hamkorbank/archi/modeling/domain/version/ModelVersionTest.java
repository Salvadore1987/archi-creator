package uz.salvadore.hamkorbank.archi.modeling.domain.version;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.event.ModelVersionCommitted;
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
        ModelVersion first = version(Optional.empty());
        ModelVersion second = version(Optional.of(first));
        ModelVersion third = version(Optional.of(second));

        assertEquals(1, first.versionNo());
        assertEquals(2, second.versionNo());
        assertEquals(3, third.versionNo());
        ModelVersion labelled = second.labelled(Optional.of("AS-IS на аудит"));
        assertEquals(2, labelled.versionNo(), "метка не меняет номера");
        assertEquals(Optional.of("AS-IS на аудит"), labelled.label());
    }

    @Test
    @DisplayName("ADR-0023: событие о фиксации рождается вместе с версией и описывает её")
    void commitBringsItsEvent() {
        ModelVersion first = version(Optional.empty());
        ModelVersion.Committed committed = ModelVersion.commit(Optional.of(first), VersionId.next(Models.UUIDS), MODEL,
                WORKSPACE, "architect-1", Optional.of("  "), Models.NOW, new byte[] {1}, HASH,
                new ModelVersion.ModelSize(12, 7));

        ModelVersionCommitted event = committed.event();
        assertEquals(MODEL, event.modelId());
        assertEquals(WORKSPACE, event.workspaceId());
        assertEquals(committed.version().versionNo(), event.versionNo());
        assertEquals(2, event.versionNo());
        assertEquals("architect-1", event.author());
        assertEquals(Optional.empty(), event.comment(), "пустой комментарий не попадает ни в версию, ни в событие");
        assertEquals(12, event.elementCount());
        assertEquals(7, event.relationshipCount());
        assertEquals(Models.NOW, event.occurredAt());
    }

    @Test
    @DisplayName("FR-47: очищенный снимок без Git — 410, восстанавливать неоткуда")
    void purgedSnapshotIsGone() {
        ModelVersion first = version(Optional.empty());
        assertArrayEquals(new byte[] {1, 2, 3}, first.requireSnapshot());

        assertThrows(ModelingException.class, () -> first.snapshotPurged(false), "без Git снимок не чистится");
        ModelVersion bound = new ModelVersion(first.id(), MODEL, 1, "a", Optional.empty(), Optional.empty(),
                first.createdAt(), first.snapshot(), HASH, Optional.of("b".repeat(40)));
        ModelingException gone = assertThrows(ModelingException.class,
                () -> bound.snapshotPurged(true).requireSnapshot());
        assertEquals(Failure.GONE, gone.failure());
    }

    static ModelVersion version(Optional<ModelVersion> last) {
        return ModelVersion.commit(last, VersionId.next(Models.UUIDS), MODEL, WORKSPACE, "architect-1",
                Optional.of("правка"), Models.NOW, new byte[] {1, 2, 3}, HASH, new ModelVersion.ModelSize(0, 0))
                .version();
    }
}
