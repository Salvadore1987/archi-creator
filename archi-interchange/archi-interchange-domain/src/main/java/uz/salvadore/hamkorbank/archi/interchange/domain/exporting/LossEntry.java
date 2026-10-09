package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import java.util.Objects;
import java.util.Set;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.LossEntryId;

/**
 * Объект, часть данных которого не перенеслась в целевой формат: что именно и почему.
 *
 * @param objectKind {@code element | relationship | view | node | edge | property}
 */
public record LossEntry(LossEntryId id, ArchiId archiId, String objectKind, String lostAspect, String reason) {

    private static final Set<String> KINDS = Set.of("element", "relationship", "view", "node", "edge", "property");

    public LossEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(lostAspect, "lostAspect");
        Objects.requireNonNull(reason, "reason");
        if (!KINDS.contains(objectKind)) {
            throw new InvalidValueException(InterchangeMessages.LOSS_UNKNOWN_KIND, objectKind);
        }
        if (lostAspect.isBlank() || reason.isBlank()) {
            throw new InvalidValueException(InterchangeMessages.LOSS_WITHOUT_REASON);
        }
    }
}
