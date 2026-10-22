package uz.salvadore.hamkorbank.archi.modeling.domain.lock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;
import uz.salvadore.hamkorbank.archi.modeling.domain.event.ModelLockReleasedEvent;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Models;

class ModelLockTest {

    private static final Instant NOW = Models.NOW;
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final EditorIdentity ALICE = EditorIdentity.of("alice", Role.ARCHITECT);
    private static final EditorIdentity BOB = EditorIdentity.of("bob", Role.ARCHITECT);
    private static final EditorIdentity ADMIN = EditorIdentity.of("root", Role.ADMIN);
    private static final ModelId MODEL = ModelId.next(Models.UUIDS);

    @Test
    @DisplayName("INV-MDL-006: запись без блокировки отклоняется с 409")
    void writeWithoutLockIsRejected() {
        ModelingDomainException noLock = assertThrows(ModelingDomainException.class,
                () -> ModelLock.requireWriteAccess(MODEL, null, "alice", NOW));

        assertEquals("INV-MDL-006", noLock.code());
        assertEquals(Failure.CONFLICT, noLock.failure());
    }

    @Test
    @DisplayName("INV-MDL-006: запись под чужой блокировкой отклоняется, отказ называет владельца")
    void writeUnderForeignLockNamesOwner() {
        ModelLock bobs = ModelLock.acquire(MODEL, null, BOB, NOW, TTL).lock();

        ModelingDomainException foreign = assertThrows(ModelingDomainException.class,
                () -> ModelLock.requireWriteAccess(MODEL, bobs, "alice", NOW));

        assertEquals("bob", foreign.details().get("lockOwner"), "клиент показывает владельца блокировки");
    }

    @Test
    @DisplayName("INV-MDL-006: своя действующая блокировка даёт право записи")
    void ownLockGrantsWriteAccess() {
        ModelLock bobs = ModelLock.acquire(MODEL, null, BOB, NOW, TTL).lock();

        assertDoesNotThrow(() -> ModelLock.requireWriteAccess(MODEL, bobs, "bob", NOW));
    }

    @Test
    @DisplayName("INV-MDL-006: просроченная блокировка прав не даёт")
    void expiredLockGrantsNoWriteAccess() {
        ModelLock alices = ModelLock.acquire(MODEL, null, ALICE, NOW, TTL).lock();
        Instant later = NOW.plus(TTL);

        assertEquals(LockStatus.EXPIRED, alices.status(later));
        assertThrows(ModelingDomainException.class,
                () -> ModelLock.requireWriteAccess(MODEL, alices, "alice", later));
    }

    @Test
    @DisplayName("INV-MDL-006: просроченную блокировку захватывает другой, прежняя закрывается как истёкшая")
    void expiredLockIsTakenOver() {
        ModelLock alices = ModelLock.acquire(MODEL, null, ALICE, NOW, TTL).lock();
        Instant later = NOW.plus(TTL);

        ModelLock.Acquisition takeover = ModelLock.acquire(MODEL, alices, BOB, later, TTL);

        assertEquals("bob", takeover.lock().owner());
        assertEquals(LockReleaseReason.EXPIRED, takeover.expiredPrevious().reason());
    }

    @Test
    @DisplayName("UC-MDL-005: второй захват чужим редактором — 409")
    void secondEditorCannotAcquire() {
        ModelLock alices = ModelLock.acquire(MODEL, null, ALICE, NOW, TTL).lock();

        ModelingDomainException second = assertThrows(ModelingDomainException.class,
                () -> ModelLock.acquire(MODEL, alices, BOB, NOW, TTL));

        assertEquals("INV-MDL-006", second.code());
    }

    @Test
    @DisplayName("UC-MDL-005: повторный захват своей блокировки продлевает её")
    void ownerReacquiringRenewsLock() {
        ModelLock alices = ModelLock.acquire(MODEL, null, ALICE, NOW, TTL).lock();

        ModelLock renewed = ModelLock.acquire(MODEL, alices, ALICE, NOW.plusSeconds(60), TTL).lock();

        assertTrue(renewed.expiresAt().isAfter(alices.expiresAt()));
    }

    @Test
    @DisplayName("UC-MDL-005: чужую блокировку архитектор не снимает — 403")
    void otherArchitectCannotRelease() {
        ModelLock alices = ModelLock.acquire(MODEL, null, ALICE, NOW, TTL).lock();

        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingDomainException.class,
                () -> alices.release(BOB, NOW)).failure());
    }

    @Test
    @DisplayName("UC-MDL-005: ADMIN снимает чужую блокировку принудительно")
    void adminForcesRelease() {
        ModelLock alices = ModelLock.acquire(MODEL, null, ALICE, NOW, TTL).lock();

        ModelLockReleasedEvent forced = alices.release(ADMIN, NOW);

        assertEquals(LockReleaseReason.FORCED_BY_ADMIN, forced.reason());
    }

    @Test
    @DisplayName("UC-MDL-005: владелец снимает свою блокировку сам")
    void ownerReleasesOwnLock() {
        ModelLock alices = ModelLock.acquire(MODEL, null, ALICE, NOW, TTL).lock();

        assertEquals(LockReleaseReason.RELEASED_BY_OWNER, alices.release(ALICE, NOW).reason());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -60})
    @DisplayName("Блокировка истекает строго позже, чем захвачена")
    void lockExpiresAfterAcquisition(long seconds) {
        InvalidValueException rejected = assertThrows(InvalidValueException.class,
                () -> ModelLock.restore(MODEL, "alice", NOW, NOW.plusSeconds(seconds)));

        assertEquals(ModelingMessages.LOCK_EXPIRES_BEFORE_ACQUIRED, rejected.reason().key());
    }
}
