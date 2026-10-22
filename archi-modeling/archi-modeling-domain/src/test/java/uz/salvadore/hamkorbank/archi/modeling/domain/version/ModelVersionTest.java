package uz.salvadore.hamkorbank.archi.modeling.domain.version;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
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
    }

    @Test
    @DisplayName("INV-MDL-010: метка версии не меняет её номера")
    void labelKeepsVersionNumber() {
        ModelVersion second = version(version(null));

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
    @DisplayName("FR-47: снимок зафиксированной версии доступен")
    void committedSnapshotIsAvailable() {
        ModelVersion first = version(null);

        assertArrayEquals(new byte[] {1, 2, 3}, first.requireSnapshot());
    }

    @Test
    @DisplayName("FR-47: без Git снимок не очищается — восстанавливать было бы неоткуда")
    void snapshotWithoutGitIsNotPurged() {
        ModelVersion first = version(null);

        assertThrows(ModelingDomainException.class, () -> first.snapshotPurged(false), "без Git снимок не чистится");
    }

    @Test
    @DisplayName("FR-47: очищенный снимок — 410")
    void purgedSnapshotIsGone() {
        ModelVersion first = version(null);
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

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("INV-MDL-010: номер версии — положительный")
    void versionNumberMustBePositive(long versionNo) {
        InvalidValueException rejected = assertThrows(InvalidValueException.class,
                () -> restored(versionNo, HASH, null, null));

        assertEquals(ModelingMessages.VERSION_NUMBER_POSITIVE, rejected.reason().key());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "abc", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    @DisplayName("Отпечаток содержимого версии — SHA-256 строчными шестнадцатеричными цифрами")
    void contentHashMustBeSha256(String hash) {
        InvalidValueException rejected = assertThrows(InvalidValueException.class,
                () -> restored(1, hash, null, null));

        assertEquals(ModelingMessages.HASH_NOT_SHA256, rejected.reason().key());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"})
    @DisplayName("Коммит опубликованной версии — полный SHA-1 строчными шестнадцатеричными цифрами")
    void gitShaMustBeFullSha1(String gitSha) {
        InvalidValueException rejected = assertThrows(InvalidValueException.class,
                () -> restored(1, HASH, null, gitSha));

        assertEquals(ModelingMessages.GIT_SHA_INVALID, rejected.reason().key());
    }

    @Test
    @DisplayName("Комментарий версии не длиннее предела")
    void commentLongerThanLimitIsRejected() {
        String comment = "к".repeat(ModelVersion.COMMENT_MAX + 1);

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> restored(1, HASH, comment, null));

        assertEquals(ModelingCodes.INVALID_INPUT, rejected.code());
        assertEquals(ModelingMessages.TOO_LONG, rejected.reason().key());
    }

    @Test
    @DisplayName("INV-MDL-010: следующая версия фиксируется после последней версии той же модели")
    void commitAfterVersionOfOtherModelIsRejected() {
        ModelVersion foreign = ModelVersion.restore(VersionId.next(Models.UUIDS), ModelId.next(Models.UUIDS), 1,
                "a", null, null, Models.NOW, new byte[] {1}, HASH, null);

        InvalidValueException rejected = assertThrows(InvalidValueException.class,
                () -> ModelVersion.commit(foreign, VersionId.next(Models.UUIDS), MODEL, WORKSPACE, "architect-1",
                        null, Models.NOW, new byte[] {1}, HASH, new ModelVersion.ModelSize(0, 0)));

        assertEquals(ModelingMessages.VERSION_OTHER_MODEL, rejected.reason().key());
    }

    @Test
    @DisplayName("FR-47: снимок помеченной версии не очищается и при Git")
    void labelledSnapshotIsNotPurged() {
        ModelVersion labelled = restored(1, HASH, null, "b".repeat(40)).labelled("Релиз 1");

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> labelled.snapshotPurged(true));

        assertEquals(ModelVersion.INVARIANT, rejected.code());
        assertEquals(Failure.CONFLICT, rejected.failure());
        assertEquals(ModelingMessages.SNAPSHOT_LABELLED, rejected.reason().key());
        assertArrayEquals(new byte[] {1, 2, 3}, labelled.requireSnapshot());
    }

    @Test
    @DisplayName("Опубликованная версия не переписывается на другой коммит")
    void publishedVersionKeepsItsCommit() {
        String first = "b".repeat(40);
        ModelVersion published = restored(1, HASH, null, first);

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> published.publishedAs("c".repeat(40)));

        assertEquals(ModelVersion.INVARIANT, rejected.code());
        assertEquals(Failure.CONFLICT, rejected.failure());
        assertEquals(ModelingMessages.VERSION_ALREADY_PUBLISHED, rejected.reason().key());
        assertEquals(first, published.gitSha());
    }

    private static ModelVersion restored(long versionNo, String hash, String comment, String gitSha) {
        return ModelVersion.restore(VersionId.next(Models.UUIDS), MODEL, versionNo, "a", comment, null, Models.NOW,
                new byte[] {1, 2, 3}, hash, gitSha);
    }
}
