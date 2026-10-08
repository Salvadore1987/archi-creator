package uz.salvadore.hamkorbank.archi.modeling.domain.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;
import uz.salvadore.hamkorbank.archi.modeling.domain.event.ModelLockReleased;
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
    @DisplayName("INV-MDL-006: запись без своей действующей блокировки отклоняется с 409")
    void writeWithoutHeldLockIsRejected() {
        ModelingException noLock = assertThrows(ModelingException.class,
                () -> ModelLock.requireWriteAccess(MODEL, Optional.empty(), "alice", NOW));
        assertEquals("INV-MDL-006", noLock.code());
        assertEquals(Failure.CONFLICT, noLock.failure());

        ModelLock bobs = ModelLock.acquire(MODEL, Optional.empty(), BOB, NOW, TTL).lock();
        ModelingException foreign = assertThrows(ModelingException.class,
                () -> ModelLock.requireWriteAccess(MODEL, Optional.of(bobs), "alice", NOW));
        assertEquals("bob", foreign.details().get("lockOwner"), "клиент показывает владельца блокировки");

        ModelLock.requireWriteAccess(MODEL, Optional.of(bobs), "bob", NOW);
    }

    @Test
    @DisplayName("INV-MDL-006: просроченная блокировка прав не даёт")
    void expiredLockGrantsNoWriteAccess() {
        ModelLock alices = ModelLock.acquire(MODEL, Optional.empty(), ALICE, NOW, TTL).lock();
        Instant later = NOW.plus(TTL);

        assertEquals(LockStatus.EXPIRED, alices.status(later));
        assertThrows(ModelingException.class,
                () -> ModelLock.requireWriteAccess(MODEL, Optional.of(alices), "alice", later));

        ModelLock.Acquisition takeover = ModelLock.acquire(MODEL, Optional.of(alices), BOB, later, TTL);
        assertEquals("bob", takeover.lock().owner());
        assertEquals(LockReleaseReason.EXPIRED, takeover.expiredPrevious().orElseThrow().reason());
    }

    @Test
    @DisplayName("UC-MDL-005: второй захват — 409, свой — продление, чужую снимает только ADMIN")
    void onlyOneEditorAndAdminForcesRelease() {
        ModelLock alices = ModelLock.acquire(MODEL, Optional.empty(), ALICE, NOW, TTL).lock();

        ModelingException second = assertThrows(ModelingException.class,
                () -> ModelLock.acquire(MODEL, Optional.of(alices), BOB, NOW, TTL));
        assertEquals("INV-MDL-006", second.code());

        ModelLock renewed = ModelLock.acquire(MODEL, Optional.of(alices), ALICE, NOW.plusSeconds(60), TTL).lock();
        assertTrue(renewed.expiresAt().isAfter(alices.expiresAt()));

        assertEquals(Failure.FORBIDDEN, assertThrows(ModelingException.class,
                () -> alices.release(BOB, NOW)).failure());
        ModelLockReleased forced = alices.release(ADMIN, NOW);
        assertEquals(LockReleaseReason.FORCED_BY_ADMIN, forced.reason());
        assertEquals(LockReleaseReason.RELEASED_BY_OWNER, alices.release(ALICE, NOW).reason());
    }
}
