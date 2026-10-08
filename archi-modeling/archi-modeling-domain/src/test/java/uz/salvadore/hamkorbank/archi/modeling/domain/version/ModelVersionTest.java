package uz.salvadore.hamkorbank.archi.modeling.domain.version;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Models;

class ModelVersionTest {

    static final String HASH = "a".repeat(64);
    static final ModelId MODEL = ModelId.next(Models.UUIDS);

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
        return ModelVersion.next(last, VersionId.next(Models.UUIDS), MODEL, "architect-1", Optional.of("правка"),
                Models.NOW, new byte[] {1, 2, 3}, HASH);
    }
}
