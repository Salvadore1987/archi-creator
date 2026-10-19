package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import java.util.Objects;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.LossEntryId;

/** Объект, часть данных которого не перенеслась в целевой формат: что именно и почему. */
public record LossEntry(LossEntryId id, ArchiId archiId, LossObjectKind objectKind, String lostAspect,
                        String reason) {

    public LossEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(objectKind, "objectKind");
        Objects.requireNonNull(lostAspect, "lostAspect");
        Objects.requireNonNull(reason, "reason");
        if (lostAspect.isBlank() || reason.isBlank()) {
            throw new InvalidValueException(InterchangeMessages.LOSS_WITHOUT_REASON);
        }
    }
}
