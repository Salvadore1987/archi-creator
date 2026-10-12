package uz.salvadore.hamkorbank.archi.modeling.domain.event;

import java.time.Instant;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.DomainEvent;
import uz.salvadore.hamkorbank.archi.modeling.domain.lock.LockReleaseReason;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/**
 * Блокировка снята — владельцем, администратором или по истечении срока.
 *
 * @param releasedBy совпадает с {@code previousOwner} при добровольном снятии
 */
public record ModelLockReleased(ModelId modelId, String previousOwner, String releasedBy, LockReleaseReason reason,
                                Instant occurredAt) implements DomainEvent {

    public ModelLockReleased {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(previousOwner, "previousOwner");
        Objects.requireNonNull(releasedBy, "releasedBy");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
